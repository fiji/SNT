#@File(style="directory") dir
#@ImageJ ij
#@LUTService lut

import groovy.io.FileType
import groovy.time.TimeCategory
import sc.fiji.snt.Tree
import sc.fiji.snt.analysis.MultiTreeColorMapper
import sc.fiji.snt.viewer.Viewer3D

/**
 * Exemplifies how to quickly render large collections of cells from
 * a directory of files.
 * 
 * 20181127: 900 MouseLight reconstructions rendered in ~30s on a
 *           4 core i7 (ubuntu 18.10) w/o a discrete graphics card!
 *
 * @author Tiago Ferreira
 */

/** Returns a list of Trees from the SWC files in the specified directory */
def getSWClist(dir) {
	list = []
	dir.eachFileMatch(FileType.ANY, ~/.*.swc/) {
		tree = new Tree(it.getAbsolutePath())
		if (tree.isEmpty()) {
			println("Skipping invalid file: "+ it)
		} else {
			println("Importing "+ it)
			list << tree
		}
	}
	list
}

// Keep track of current time
start = new Date()

// Retrieve the Tree list
trees = getSWClist(dir)
if (trees.isEmpty()) {
	println("No files found in "+ dir)
	return
}

// Define the color table (LUT) and perform the color mapping to total length.
// A fixed set of tables can be accessed from net.imagej.display.ColorTables, 
// e.g., `colorTable = ColorTables.ICE`, but using LutService, one can access
// _any_ LUT currently installed in Fiji
colorTable = lut.loadLUT(lut.findLUTs().get("Ice.lut"))
colorMapper = new MultiTreeColorMapper(trees)
colorMapper.map(MultiTreeColorMapper.TOTAL_LENGTH, colorTable)

// Initialize a non-interactive Reconstruction Viewer
viewer = (trees.size > 100) ? new Viewer3D() : new Viewer3D(ij.context())

// Plot all trees
trees.forEach({
	println("Rendering "+ it)
	viewer.add(it)
})

// Add Legend
limits = colorMapper.getMinMax()
viewer.addColorBarLegend(colorTable, (float)limits[0], (float)limits[1])

// Show result
viewer.show()
viewer.setAnimationEnabled(true)
td = TimeCategory.minus(new Date(), start)
println("Length range: " + limits[0] + "---" + limits[1])
println("Rendered " + trees.size() + " files in "+ td)
println("With Viewer active, Press 'H' for a list of Viewer's shortcuts")

