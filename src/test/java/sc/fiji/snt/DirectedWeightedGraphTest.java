package sc.fiji.snt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.ParanoidGraph;
import org.jgrapht.GraphTests;
import org.jgrapht.Graphs;

import sc.fiji.snt.analysis.TreeAnalyzer;
import sc.fiji.snt.analysis.graph.DirectedWeightedGraph;
import sc.fiji.snt.analysis.graph.SWCWeightedEdge;
import sc.fiji.snt.util.PointInImage;
import sc.fiji.snt.util.SWCPoint;

/**
 * Tests for {@link DirectedWeightedGraph}
 *
 * @author Tiago Ferreira
 * @author Cameron Arshadi
 */
public class DirectedWeightedGraphTest {

	private final double precision = 0.0001;
	private Tree tree;
	private TreeAnalyzer analyzer;

	@Before
	public void setUp() throws Exception {
		tree = new SNTService().demoTree();
		analyzer = new TreeAnalyzer(tree);
		assumeNotNull(tree);
	}

	public void testModificationAndConversionToTree() throws InterruptedException {
		Set<SWCPoint> points = new HashSet<SWCPoint>();
		SWCPoint v1 = new SWCPoint(1, 2, 1.0, 1.0, 1.0, 0.5, -1);
		SWCPoint v2 = new SWCPoint(2, 2, 1.0, 4.0, 1.0, 2.515, 1);
		SWCPoint v3 = new SWCPoint(3, 2, 4.0, 4.0, 8.0, 3.2, 2);
		SWCPoint v4 = new SWCPoint(4, 2, 9.0, 12.0, 2.0, 3.2, 3);
		points.add(v1);
		points.add(v2);
		points.add(v3);
		points.add(v4);
		
		Tree tree = new Tree(points, "");

		DirectedWeightedGraph graph = tree.getGraph();
		
		SWCPoint oldRoot = graph.getRoot();
		SWCPoint t = graph.getTips().get(0);
		SWCPoint tp = Graphs.predecessorListOf(graph, t).get(0);
		graph.removeEdge(tp, t);
		
		SWCPoint secondPoint = Graphs.successorListOf(graph, oldRoot).get(0);
		graph.addEdge(secondPoint, t);
		
		SWCPoint newRoot = new SWCPoint(0, 2, 0.5, 0.5, 0.5, 1.0, 0);
		graph.addVertex(newRoot);
		graph.addEdge(newRoot, oldRoot);
		
		Tree changedTree = graph.getTree();
		TreeAnalyzer analyzer = new TreeAnalyzer(changedTree);
		
		PointInImage newTreeRoot = changedTree.getRoot();
		assertTrue("Graph to Tree: replace root", newTreeRoot.getX() == 0.5 && newTreeRoot.getY() == 0.5 && newTreeRoot.getZ() == 0.5);
		assertTrue("Graph to Tree: # Branches", analyzer.getBranches().size() == 3);
		assertTrue("Graph to Tree: Strahler #", analyzer.getStrahlerNumber() == 2);
		assertTrue("Graph to Tree: # Branch Points", analyzer.getBranchPoints().size() == 1);
		assertTrue("Graph to Tree: # Tips", analyzer.getTips().size() == 2);
		assertEquals(analyzer.getCableLength(), (newRoot.distanceTo(v1) + v1.distanceTo(v2) + v2.distanceTo(v3) + v2.distanceTo(v4)), precision);

	}

	@Test
	public void testDirectedWeightedGraph() throws InterruptedException {

		// First test that #equals() and #hashCode() are correctly set up for the vertex
		// Type
		DefaultDirectedWeightedGraph<SWCPoint, SWCWeightedEdge> badGraph = new DefaultDirectedWeightedGraph<SWCPoint, SWCWeightedEdge>(
				SWCWeightedEdge.class);
		ParanoidGraph<SWCPoint, SWCWeightedEdge> pGraph = new ParanoidGraph<SWCPoint, SWCWeightedEdge>(badGraph);
		SWCPoint v1 = new SWCPoint(0, 2, 1.0, 1.0, 1.0, 1.0, 0);
		SWCPoint v2 = new SWCPoint(0, 2, 2.0, 2.0, 2.0, 1.0, 0);
		SWCPoint v3 = v1;
		pGraph.addVertex(v1);
		pGraph.addVertex(v2);
		try {
			boolean addSuccess = pGraph.addVertex(v3);
			assertFalse(addSuccess);
		} catch (IllegalArgumentException ex) {
			ex.printStackTrace();
			fail();
		}

		testModificationAndConversionToTree();

		DirectedWeightedGraph graph = tree.getGraph();

		final int numVertices = graph.vertexSet().size();
		final int numRoots = graph.vertexSet().stream().filter(v -> graph.inDegreeOf(v) == 0)
				.collect(Collectors.toList()).size();
		final int numBranchPoints = graph.getBPs().size();
		final int numTips = graph.getTips().size();
		final double summedEdgeWeights = graph.sumEdgeWeights();
		

		// Compare measurements against TreeAnalyzer since TreeAnalyzer is compared
		// against the hard-coded correct values
		assertTrue("Graph: Equal # Vertices", numVertices == tree.getNodes().size());
		assertTrue("Graph: Single Root", numRoots == 1);
		assertTrue("Graph: Equal # Branch Points", numBranchPoints == analyzer.getBranchPoints().size());
		assertTrue("Graph: Equal # End Points", numTips == analyzer.getTips().size());
		assertEquals("Graph: Summed Edge Weights", summedEdgeWeights, analyzer.getCableLength(), precision);

		// Topology tests
		assertTrue("Graph: Is Simple", GraphTests.isSimple(graph));
		assertTrue("Graph: Is connected", GraphTests.isConnected(graph));
		assertTrue(GraphTests.requireDirected(graph) instanceof DirectedWeightedGraph);
		assertTrue(GraphTests.requireWeighted(graph) instanceof DirectedWeightedGraph);

		// Graph scaling tests
		for (double scaleFactor : new double[] { .25d, 1d, 2d }) {
			graph.scale(scaleFactor, scaleFactor, scaleFactor, true);
			assertTrue("Graph: Is Simple", GraphTests.isSimple(graph));
			assertTrue("Graph: Is connected", GraphTests.isConnected(graph));
			assertTrue("Graph Scaling: Equal # Tips", graph.getTips().size() == numTips);
			assertTrue("Graph Scaling: Equal # Branch points", graph.getBPs().size() == numBranchPoints);
			assertTrue("Graph Scaling: Equal # Vertices", graph.vertexSet().size() == numVertices);
			assertTrue("Graph Scaling: Equal # Roots", graph.vertexSet().stream().filter(v -> graph.inDegreeOf(v) == 0)
					.collect(Collectors.toList()).size() == numRoots);
			assertEquals("Graph Scaling: Summed Edge Weight", graph.sumEdgeWeights(), summedEdgeWeights * scaleFactor,
					precision);
			graph.scale(1 / scaleFactor, 1 / scaleFactor, 1 / scaleFactor, true);
		}

	}

}