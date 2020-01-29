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

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import sc.fiji.snt.analysis.TreeAnalyzer;
import sc.fiji.snt.util.PointInImage;

/**
 * Tests for {@link TreeAnalyzer}s
 *
 * @author Tiago Ferreira
 */
public class SWCTest {

	private List<Tree> trees;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Before
	public void setUp() throws Exception {
		trees = new SNTService().demoTrees();
		assumeNotNull(trees);
	}

	@Test
	public void testTreeIO() {
		trees.forEach(tree -> {
			final List<PointInImage> nodes = tree.getNodes();
			assertTrue(nodes.size() == tree.getNodesAsSWCPoints().size());
			final double cableLength = new TreeAnalyzer(tree).getCableLength();
			final Set<PointInImage> bps = new TreeAnalyzer(tree).getBranchPoints();
			try {

				final String filePath = folder.newFile(tree.getLabel() + ".swc").getAbsolutePath();
				assertTrue(tree.saveAsSWC(filePath));
				final Tree savedTree = new Tree(filePath);
				// Did tree change?
				assertTrue(nodes.size() == savedTree.getNodes().size());
				assertTrue(bps.equals(new TreeAnalyzer(tree).getBranchPoints()));
				assertTrue(cableLength == new TreeAnalyzer(tree).getCableLength());

			} catch (final IOException e) {
				e.printStackTrace();
			}
		});
	}

}
