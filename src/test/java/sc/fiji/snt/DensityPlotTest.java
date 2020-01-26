package sc.fiji.snt;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageConverter;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import sc.fiji.snt.hyperpanes.MultiDThreePanes;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DensityPlotTest {
    static public void densityPlot(String swcDirectory, String outDirectory) throws IOException {
        List<Tree> trees = Tree.listFromDir(swcDirectory);

        int[] maxDims = new int[]{0, 0, 0};

        new ij.ImageJ();

        int numTrees = 50;

        for( Tree tree : trees ) {
            System.out.println("Processing tree: " + tree);
            System.out.println(tree.getBoundingBox());
//            # retrieve an empty image capable of hosting the rasterized tree
            ImagePlus imp = tree.getImpContainer(MultiDThreePanes.XY_PLANE, 32);
//            # imp is now an 8-bit image. We need it to be 16. (Sorry for using IJ1)
            new ImageConverter(imp).convertToGray16();
//            # skeletonize the image in place
            tree.skeletonize(imp, 1);//# integer intensity of skeleton
//            # save skeleton
            ij.IJ.saveAsTiff(imp, outDirectory + tree.getLabel() + ".tif");
            // save the size of the images
            maxDims[0] = Math.max(maxDims[0], imp.getWidth());
            maxDims[1] = Math.max(maxDims[1], imp.getHeight());
            maxDims[2] = Math.max(maxDims[2], imp.getImageStackSize());

            //imp.show();

            numTrees--;

            if( numTrees < 0 )
                break;
        }

        //ImageJ imagej = new ImageJ();

        // make an image of the largest size
        ImagePlus imp = IJ.createImage("outImg", "float32", maxDims[0], maxDims[1], maxDims[2]);

        Img<FloatType> outImg = ImageJFunctions.wrap(imp);

        File[] listing = new File(outDirectory).listFiles();
        for( File imf : listing ) {
            if( imf.getName().contains("density_plot") )
                continue;

            Img<UnsignedShortType> im = ImageJFunctions.wrap(IJ.openImage(imf.getAbsolutePath()));

            long[] offset = new long[3];
            for( int d = 0; d < im.numDimensions(); d++ ){
                offset[d] = ( outImg.dimension(d) - im.dimension(d) ) / 2;
            }

            IntervalView<FloatType> outView = Views.interval(outImg, Views.translate(im, offset));

            Cursor<UnsignedShortType> inCur = Views.flatIterable(im).cursor();
            Cursor<FloatType> outCur = Views.flatIterable(outView).cursor();
            while( inCur.hasNext() ) {
                inCur.fwd();
                outCur.fwd();
                outCur.get().set( (float) outCur.get().get() + inCur.get().get() );
            }


            //IntervalView<UnsignedShortType> ivl = Views.translate(im, offset);
        }



//        ImagePlus outImp = ImageJFunctions.wrap(outImg, "densityPlot");
        ij.IJ.saveAsTiff(imp, outDirectory + "density_plot.tif");

        imp.show();
    }

    static public void main(String[] args) throws IOException {
        String parentDirectory = System.getProperty("user.home") + "/Dropbox/SNTmanuscript/Simulations/GRNFinalAnalysis";
        densityPlot(parentDirectory + "/grn1/",
                parentDirectory + "/grn1img/");
    }

}
