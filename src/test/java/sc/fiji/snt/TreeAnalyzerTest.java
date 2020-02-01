/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2019 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package sc.fiji.snt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import org.junit.Before;
import org.junit.Test;

import sc.fiji.snt.analysis.TreeAnalyzer;

/**
 * Tests for {@link TreeAnalyzer}s
 *
 * @author Tiago Ferreira
 */
public class TreeAnalyzerTest {

	private Tree tree;
	private TreeAnalyzer analyzer;

	@Before
	public void setUp() throws Exception {
		tree = new SNTService().demoTree();
		analyzer = new TreeAnalyzer(tree);
		assumeNotNull(tree);
	}

	@Test
	public void testAnalyzer() {
		assertTrue("# Paths = 16", analyzer.getNPaths() == 16);
		assertTrue("# Branch points = 15", analyzer.getBranchPoints().size() == 15);
		assertTrue("# Tips = 16", analyzer.getTips().size() == 16);
		assertTrue("# I paths = 1", analyzer.getPrimaryPaths().size() == 1);
		assertTrue("# Highest path order = 5", analyzer.getHighestPathOrder() == 5);
		final double cableLength =  analyzer.getCableLength();
		assertTrue("569.34 > Sum length of all paths < 569.35um", cableLength > 569.34 && cableLength < 569.35);
		final double primaryLength =  analyzer.getPrimaryLength();
		assertTrue("50.99 > Sum length of I branches < 51.01um", primaryLength > 50.99 && primaryLength < 51.01);
		final double terminalLength =  analyzer.getTerminalLength();
		assertTrue("153.29um > Sum length of terminal paths < 153.30um", terminalLength > 153.29 && terminalLength < 153.30);
		final double avgBranchLength =  analyzer.getAvgBranchLength();
		assertTrue("144.35um > Avg branch length < 144.34um", avgBranchLength > 144.34 && avgBranchLength < 144.35);
		assertTrue("Strahler number: 5", analyzer.getStrahlerNumber() == 5);
		assertTrue("Strahler bif. ratio: 2", analyzer.getStrahlerBifurcationRatio() == 2);
		assertTrue("N Branches: 31", analyzer.getNBranches() == 31);
		assertTrue("Width = 116.0", analyzer.getWidth() == 116d);
		assertTrue("Height = 145.0", analyzer.getHeight() == 145d);
		assertTrue("Depth = 0.0", analyzer.getDepth() == 0d);
		final double avgContraction=  analyzer.getAvgContraction();
		assertTrue("0.1231 > Avg contraction < 0.1230", avgContraction > 0.1230 && avgContraction < 0.1231);

		// Scaling tests
		for (double scaleFactor : new double[] { .25d, .5d, 11.87d}) {
			tree.scale(scaleFactor, scaleFactor, scaleFactor);
			final TreeAnalyzer scaledAnalyzer = new TreeAnalyzer(tree);
			assertTrue("Scaling: Equal # Tips", analyzer.getTips().size() == scaledAnalyzer.getTips().size());
			assertTrue("Scaling: Equal # Branch points", analyzer.getBranchPoints().size() == scaledAnalyzer.getBranchPoints().size());
			assertTrue("Scaling: Equal # Branches", analyzer.getNBranches() == scaledAnalyzer.getNBranches());
			assertEquals("Scaling: Cable length", cableLength * scaleFactor, scaledAnalyzer.getCableLength(), 0.1);
			tree.scale(1 / scaleFactor, 1 / scaleFactor, 1 / scaleFactor);
		}
	}

}
