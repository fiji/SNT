package sc.fiji.snt;

import cleargl.GLVector;
import graphics.scenery.*;
import net.imagej.ImageJ;
import net.imglib2.display.ColorTable;
import org.jzy3d.colors.ISingleColorable;
import org.jzy3d.plot3d.primitives.AbstractWireframeable;
import org.scijava.util.ColorRGB;
import org.scijava.util.Colors;
import sc.fiji.snt.gui.GuiUtils;
import sc.fiji.snt.util.PointInImage;
import sc.iview.SciView;
import sc.iview.shape.Line3D;
import sc.iview.vector.ClearGLVector3;
import sc.iview.vector.DoubleVector3;
import sc.iview.vector.FloatVector3;
import sc.iview.vector.Vector3;

import java.awt.*;
import java.util.List;
import java.util.*;

public class SciViewSNT {
    private final static String PATH_MANAGER_TREE_LABEL = "Path Manager Contents";

    protected SciView sciView;
    private Map<String, Node> plottedTrees;

    public SciViewSNT() {
        plottedTrees = new TreeMap<String,Node>();
    }

    public SciView getSciView() {
        return sciView;
    }

    public void setSciView(SciView sciView) {
        this.sciView = sciView;
    }

	private void addItemToManager(final String label) {

	}

	private String makeUniqueKey(final Map<String, ?> map, final String key) {
		for (int i = 2; i <= 100; i++) {
			final String candidate = key + " (" + i + ")";
			if (!map.containsKey(candidate)) return candidate;
		}
		return key + " (" + UUID.randomUUID() + ")";
	}

	private String getUniqueLabel(final Map<String, ?> map,
		final String fallbackPrefix, final String candidate)
	{
		final String label = (candidate == null || candidate.trim().isEmpty())
			? fallbackPrefix : candidate;
		return (map.containsKey(label)) ? makeUniqueKey(map, label) : label;
	}

    /**
	 * Adds a tree to this viewer.
	 *
	 * @param tree the {@link Tree} to be added. The Tree's label will be used as
	 *          identifier. It is expected to be unique when rendering multiple
	 *          Trees, if not (or no label exists) a unique label will be
	 *          generated.
	 * @see Tree#getLabel()
	 */
	public void add(final Tree tree) {
		final String label = getUniqueLabel(plottedTrees, "Tree ", tree.getLabel());
		final ShapeTree shapeTree = new ShapeTree(tree);
		plottedTrees.put(label, shapeTree);
		addItemToManager(label);
		for( Node node : shapeTree.get().getChildren() ) {
		    //System.out.println("addTree: node " + node.getMetadata().get("pathID"));
		    sciView.addNode(node);
        }
		//sciView.getCamera().setPosition(treeCenter.minus(new GLVector(0,0,-10f)));
        sciView.centerOnNode( shapeTree );
	}


    public boolean syncPathManagerList() {
        if (SNTUtils.getPluginInstance() == null) throw new IllegalArgumentException(
                "SNT is not running.");
        final Tree tree = new Tree(SNTUtils.getPluginInstance().getPathAndFillManager()
                .getPathsFiltered());
        if (plottedTrees.containsKey(PATH_MANAGER_TREE_LABEL)) {// PATH_MANAGER_TREE_LABEL, the value of this is the *new* tree to add
            // TODO If the Node exists, then remove and add new one to replace
            for( Node node : plottedTrees.get(PATH_MANAGER_TREE_LABEL).getChildren() ) {
                syncNode(tree,node);
                //sciView.deleteNode(node);
            }
        }
        else {
            tree.setLabel(PATH_MANAGER_TREE_LABEL);
			add(tree);
        }
        return true;
    }

    private void syncNode(Tree tree, Node node) {
        // Every Path in tree has a unique getID()
        // Node should know this ID for syncing
        Integer pathID = (Integer) node.getMetadata().get("pathID");
        if( pathID != null ) {
            for (final Path p : tree.list()) {
                if (p.getID() == pathID) {
                    // Sync the path to the node

                    break;
                }
            }
        }
    }

    private class ShapeTree extends Node {

        private static final float SOMA_SCALING_FACTOR = 2.5f;
        private static final float SOMA_SLICES = 15f; // Sphere default;
        private static final float DEF_NODE_RADIUS = 3f;

        private final Tree tree;
        private Node treeSubShape;
        private Node somaSubShape;
        private Vector3 translationReset;

        public ShapeTree(final Tree tree) {
            super();
            this.tree = tree;
            translationReset = new FloatVector3(0f,0f,0f);
        }

        public void setSomaDisplayed(final boolean displayed) {
            //if (somaSubShape != null) somaSubShape.setDisplayed(displayed);
        }

        public void setArborDisplayed(final boolean displayed) {
            //if (treeSubShape != null) treeSubShape.setDisplayed(displayed);
        }

        public Node get() {
            //if (components == null || components.isEmpty()) assembleShape();
            assembleShape();
            return this;
        }

        public void translateTo(final Vector3 destination) {
            translationReset.setPosition(destination);
        }

        public void resetTranslation() {
            translateTo(translationReset);
            translationReset = new FloatVector3(0f, 0f, 0f);
        }

        private void assembleShape() {

            final List<Node> lines = new ArrayList<>();
            final List<PointInImage> somaPoints = new ArrayList<>();
            final List<Color> somaColors = new ArrayList<>();

            float defThickness = 3f;

            for (final Path p : tree.list()) {

                // Stash soma coordinates
                if (Path.SWC_SOMA == p.getSWCType()) {
                    for (int i = 0; i < p.size(); i++) {
                        final PointInImage pim = p.getNode(i);
                        pim.v = p.getNodeRadius(i);
                        somaPoints.add(pim);
                    }
                    if (p.hasNodeColors()) {
                        somaColors.addAll(Arrays.asList(p.getNodeColors()));
                    }
                    else {
                        somaColors.add(p.getColor());
                    }
                    continue;
                }

                // Assemble arbor(s)
                //final Line line = new Line();
                //line.setCapacity(p.size());
                //points = new
                List<Vector3> points = new ArrayList<>();
                List<ColorRGB> colors = new ArrayList<>();
                //ColorRGB color = new ColorRGB(255,0,0);
                float scaleFactor = 0.1f;
                for (int i = 0; i < p.size(); ++i) {
                    final PointInImage pim = p.getNode(i);
                    final ClearGLVector3 coord = new ClearGLVector3((float)pim.x, (float)pim.y, (float)pim.z);
                    final Material mat = new Material();
                    Color c = p.hasNodeColors() ? p.getNodeColor(i) : p.getColor();
                    ColorRGB color = c == null ? Colors.ANTIQUEWHITE : fromAWTColor(c);
                    mat.setDiffuse(new GLVector(color.getRed(),color.getGreen(),color.getBlue()));
                    final float width = Math.max((float) p.getNodeRadius(i),
                            DEF_NODE_RADIUS);
                    //System.out.println( "(point " + i + " " + coord.source() + ")" );
                    points.add( new FloatVector3(coord.source().x()*scaleFactor,coord.source().y()*scaleFactor,coord.source().z()*scaleFactor) );
                    colors.add( color );

                    //line.addPoint(coord.source());
//                    if( i > 0 ) {
//                        Cylinder c = Cylinder.betweenPoints(ClearGLVector3.convert(points[i - 1]), ClearGLVector3.convert(points[i]), 0.05f, 1f, 18);
//                        line.addLine(c);
//                    }
                }

                Line3D line = new Line3D(points, colors, 0.05);
                line.getMetadata().put("pathID",p.getID());
                //sciView.addNode(line,false );
                lines.add(line);
            }

            // Group all lines into a Composite. BY default the composite
            // will have no wireframe color, to allow colors for Paths/
            // nodes to be revealed. Once a wireframe color is explicit
            // set it will be applied to all the paths in the composite
            if (!lines.isEmpty()) {
                for( Node line : lines ) {
                    addChild( line );
                }
            }
            assembleSoma(somaPoints, somaColors);
            if (somaSubShape != null) addChild(somaSubShape);

            //sciView.addNode(this, true);

            this.setPosition(this.getMaximumBoundingBox().getBoundingSphere().getOrigin());
            sciView.setActiveNode(this);
            sciView.surroundLighting();
            // shape.setFaceDisplayed(true);
            // shape.setWireframeDisplayed(true);
        }

        private void assembleSoma(final List<PointInImage> somaPoints,
                                  final List<Color> somaColors)
        {
            //ColorRGB col = fromAWTColor(SNTColor.average(somaColors));
            ColorRGB col = new ColorRGB(0,255,0);
            switch (somaPoints.size()) {
                case 0:
                    //SNT.log(tree.getLabel() + ": No soma attribute");
                    somaSubShape = null;
                    return;
                case 1:
                    // single point soma: http://neuromorpho.org/SomaFormat.html
                    final PointInImage sCenter = somaPoints.get(0);
                    somaSubShape = sciView.addSphere(convertPIIToVector3(sCenter), DEF_NODE_RADIUS, col);
                    return;
                case 3:
                    // 3 point soma representation: http://neuromorpho.org/SomaFormat.html
                    final Vector3 p1 = convertPIIToVector3(somaPoints.get(0));
                    final Vector3 p2 = convertPIIToVector3(somaPoints.get(1));
                    final Vector3 p3 = convertPIIToVector3(somaPoints.get(2));
                    final double lenthT1 = p2.minus(p1).getLength();
                    final double lenthT2 = p1.minus(p3).getLength();
                    final Node t1 = sciView.addCylinder(p2,DEF_NODE_RADIUS,(float)lenthT1,20);
                    final Node t2 = sciView.addCylinder(p1,DEF_NODE_RADIUS,(float)lenthT2,20);
                    addChild(t1);
                    addChild(t2);
                    return;
                default:
                    // just create a centroid sphere
                    final PointInImage cCenter = PointInImage.average(somaPoints);
                    somaSubShape = sciView.addSphere(convertPIIToVector3(cCenter), DEF_NODE_RADIUS, col);
                    return;
            }
        }

        private Vector3 convertPIIToVector3(PointInImage sCenter) {
            Vector3 v = new DoubleVector3(sCenter.x,sCenter.y,sCenter.z);
            return v;
        }

        private ColorRGB fromAWTColor(Color average) {
            return new ColorRGB(average.getRed(),average.getGreen(),average.getBlue());
        }

        private <T extends AbstractWireframeable & ISingleColorable> void
        setWireFrame(final T t, final float r, final Color color)
        {

        }

//        private Cylinder tube(final PointInImage bottom, final PointInImage top,
//                          final Color color)
//        {
//            final Tube tube = new Tube();
//            tube.setPosition(new Coord3d((bottom.x + top.x) / 2, (bottom.y + top.y) /
//                    2, (bottom.z + top.z) / 2));
//            final float height = (float) bottom.distanceTo(top);
//            tube.setVolume((float) bottom.v, (float) top.v, height);
//            return tube;
//        }
//
//        private Sphere sphere(final PointInImage center, final Color color) {
//            final Sphere s = new Sphere();
//            s.setPosition(new Coord3d(center.x, center.y, center.z));
//            final float radius = (float) Math.max(center.v, SOMA_SCALING_FACTOR *
//                    defThickness);
//            s.setVolume(radius);
//            setWireFrame(s, radius, color);
//            return s;
//        }

        public void rebuildShape() {
//            if (isDisplayed()) {
//                clear();
//                assembleShape();
//            }
        }

        public void setThickness(final float thickness) {
            //treeSubShape.setWireframeWidth(thickness);
        }

        private void setArborColor(final ColorRGB color) {
            //setArborColor(fromColorRGB(color));
        }

        private void setArborColor(final Color color) {
            //treeSubShape.setWireframeColor(color);
//			for (int i = 0; i < treeSubShape.size(); i++) {
//				((LineStrip) treeSubShape.get(i)).setColor(color);
//			}
        }

        private Color getArborWireFrameColor() {
            //return (treeSubShape == null) ? null : treeSubShape.getWireframeColor();
            return null;
        }

        private Color getSomaColor() {
            //return (somaSubShape == null) ? null : somaSubShape.getWireframeColor();
            return null;
        }

        private void setSomaColor(final Color color) {
            //if (somaSubShape != null) somaSubShape.setWireframeColor(color);
        }

        public void setSomaColor(final ColorRGB color) {
            //setSomaColor(fromColorRGB(color));
        }

        public double[] colorize(final String measurement,
                                 final ColorTable colorTable)
        {
//            final TreeColorMapper colorizer = new TreeColorMapper();
//            colorizer.map(tree, measurement, colorTable);
//            rebuildShape();
            //return colorizer.getMinMax();
            return null;
        }

        /**
         * Generates an [OrientedBoundingBox] for this [Node]. This will take
         * geometry information into consideration if this Node implements [HasGeometry].
         * In case a bounding box cannot be determined, the function will return null.
         */
        public OrientedBoundingBox generateBoundingBox() {
            OrientedBoundingBox bb = new OrientedBoundingBox(this, 0.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 0.0f);
            for( Node n : getChildren() ) {
                OrientedBoundingBox cBB = n.generateBoundingBox();
                if( cBB != null )
                    bb = bb.expand(bb, cBB);
            }
            return bb;
        }
    }

    /* IDE debug method */
	public static void main(final String[] args) throws InterruptedException {
	    SceneryBase.xinitThreads();
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();



		//SciView sciView = ij.context().getService(SciViewService.class).getOrCreateActiveSciView();
		//final Tree tree = new Tree("/home/tferr/code/test-files/AA0100.swc");
//		final Tree tree = new Tree("/home/kharrington/Dropbox/quickGitBackup/instar-superstar/data/pair/A02m_a3l Pseudolooper-3_406883.swc");
//		final TreeColorMapper colorizer = new TreeColorMapper(ij.getContext());
//		colorizer.map(tree, TreeColorMapper.BRANCH_ORDER, ColorTables.ICE);
//		final double[] bounds = colorizer.getMinMax();
//		SNT.setDebugMode(true);
//		final Viewer3D jzy3D = new Viewer3D(ij.context());
//		jzy3D.addColorBarLegend(ColorTables.ICE, (float) bounds[0],
//			(float) bounds[1], new Font("Arial", Font.PLAIN, 24), 3, 4);
//		jzy3D.add(tree);
//		//final OBJMesh brainMesh = jzy3D.loadMouseRefBrain();
//		//brainMesh.setBoundingBoxColor(Colors.RED);
//		final TreeAnalyzer analyzer = new TreeAnalyzer(tree);
//		jzy3D.addSurface(analyzer.getTips());
//		jzy3D.show();
//		jzy3D.setAnimationEnabled(true);
//		jzy3D.setViewPoint(-1.5707964f, -1.5707964f);
//		jzy3D.updateColorBarLegend(-8, 88);
	}
}
