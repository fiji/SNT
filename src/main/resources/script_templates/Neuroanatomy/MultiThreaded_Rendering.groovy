#@File(style="directory") dir

import groovy.io.FileType

import net.imglib2.display.ColorTable
import net.imagej.display.ColorTables
import org.scijava.util.ColorRGB

import tracing.Tree
import tracing.plot.TreePlot3D
import tracing.analysis.TreeAnalyzer


/**
 * Exemplifies how to measure and render a large collection of files using 
 * multiple threads.
 *
 * @author Tiago Ferreira
 */

/** Returns a list of all SWC files in the specified directory */
def getSWClist(dir) {
	list = []
	dir.eachFileMatch(FileType.ANY, ~/.*.swc/) {list << it}
	list
}

/** Tries to guess the longest tree in a list of SWC files */
void computemaxLength(fileList) {
	largestFileSize = 0L
	largestFile = null
	for (f in fileList) {
		if (f.length() > largestFileSize) {
			largestFileSize = f.length()
			largestFile = f
		}
	}
	maxTree = new Tree(largestFile.getAbsolutePath())
	maxLength = getCableLength(maxTree)
	println("Mapping lengths to largest file "+ largestFile + ": Max length:" + maxLength)
}

/** Computes the cable length of the specified Tree */
float getCableLength(tree) {
	new TreeAnalyzer(tree).getCableLength()
}

/* 
 * Computes the cable length of the specified Tree and maps it to the global
 * length color table
 */
void colorizeTree(tree) {
	//argb = colorTable.lookupARGB(minLength, getCableLength(tree), maxLength)
	//color = new Color(argb, false)
	length = getCableLength(tree)
	println("..."+ tree.getLabel() + " length: "+ length)
	int idx
	if (length <= minLength)
		idx = 0
	else if (length > maxLength)
		idx = colorTable.getLength() - 1
	else
		idx = (int) Math.round((colorTable.getLength() - 1) * (length - minLength) / (maxLength - minLength))
	color = new ColorRGB(colorTable.get(ColorTable.RED,idx), colorTable.get(ColorTable.GREEN,idx), colorTable.get(ColorTable.BLUE,idx))
	tree.setColor(color)
}


// Retrieve the file list
files = getSWClist(dir)
if (files.isEmpty()) {
	println("No files found in "+ dir)
	return
}

// Define the LUT for color mapping
colorTable = ColorTables.ICE

// Define smallest and largest Tree lengths
minLength = 0f
maxLength = 0f
computemaxLength(files)

// Initialize a non-interactive Reconstruction Viewer
viewer = new TreePlot3D()

// Measure and plot each tree using all (n-1) available cores
files.stream().parallel().forEach({
	tree = new Tree(it.getAbsolutePath())
	if (tree.isEmpty()) {
		println("Skipping invalid file: "+ tree.getLabel())
	} else {
		colorizeTree(tree)
		viewer.add(tree)
	}
})

if (viewer.getTrees().size() == 0) {
	println("No files imported. Invalid Directory?")
	return
}

// Add Legend and show the result
viewer.addColorBarLegend(colorTable, minLength, maxLength)
viewer.show()
println("Done. With Viewer active, Press 'H' for a list of Viewer's shortcuts")

