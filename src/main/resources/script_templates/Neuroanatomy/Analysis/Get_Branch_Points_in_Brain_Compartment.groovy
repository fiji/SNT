# @ImageJ ij

import sc.fiji.snt.*
import sc.fiji.snt.io.*
import sc.fiji.snt.analysis.*
import sc.fiji.snt.annotation.*
import sc.fiji.snt.viewer.*
import sc.fiji.snt.util.*
import org.scijava.util.*


/**
 * file:	Get_Branch_Points_in_Brain_Compartment.groovy
 * author:	Tiago Ferreira  
 * version:	20190613 
 * info:	Exemplifies how to extract morphometric properties of a MouseLight
 *			cell associated with a specific brain region/neuropil compartment.
 *			Requires internet connection.
 */


// First we specify the id of the cell we are going to parse and the brain
// compartments we are interested in:
cellId = "AA0596"
somaCompartment = AllenUtils.getCompartment("Somatomotor areas")
thalamus = AllenUtils.getCompartment("Thalamus")

// First we define a Loader for the MouseLightDatabase:
loader = new MouseLightLoader(cellId)
if (!loader.idExists()) {
	println(" id not found. Aborting...")
	return null
}

// Now we extract all the axonal branch points in the Thalamus.
// See method below for detaisl:
bps = getFilteredBranchPoints(loader)
if (!bps) {
  println(" Bummer! No branch points are associated with $thalamus")
  return
}

// Let's look at the soma location of the analyzed cell, and the centroid
// defined by the extracted branch points. 
somaCentroid = SNTPoint.average(loader.getNodes("soma"))
bpsCentroid = SNTPoint.average(bps)
println("SomaLocation: $somaCentroid.x, $somaCentroid.y, $somaCentroid.z")
println("Centroid of BPs in ${thalamus.name()}: $bpsCentroid.x, $bpsCentroid.y, $bpsCentroid.z")

// Let's find out how many of the branch points are in the left hemisphere
// (NB: In the ML database the medial-Lateral axis is mapped to the X-axis)
midline = AllenUtils.brainCenter().getX()
nodeStats = new NodeStatistics(bps, loader.getTree("axon"))
hist = nodeStats.getHistogram("x coordinates")
hist.annotateXline(midline, "midline")
hist.annotatePoint(somaCentroid.getX(), 0, "soma")
hist.show()

// Let's retrieve some details about the branches associated with the
// extracted branch points:
hist = nodeStats.getHistogram("branch order")
hist.show()

// Let's assemble a Reconstruction Viewer to visualize the data
viewer = new Viewer3D(ij.context())

// Add relevant meshes
brainMesh = AllenUtils.getCompartment("Whole Brain").getMesh()
viewer.add(brainMesh)
viewer.add(somaCompartment.getMesh())
viewer.add(thalamus.getMesh())

// Add original cell
tree = loader.getTree()
tree.setColor("yellow")
viewer.add(tree)

// Add extracted branch points color-coded by order
mapper = new NodeColorMapper(nodeStats, ij.context())
mapper.map("branch length", "Ice.lut")
annot = viewer.annotatePoints(bps, "Thalamic BPs")
annot.setSize(10)

// Annotate centroids of soma and BPs
miscAnnotations = []
miscAnnotations << viewer.annotatePoint(somaCentroid, "Soma")
miscAnnotations << viewer.annotatePoint(bpsCentroid, "BPs Barycenter")
for (annot in miscAnnotations) {
	annot.setColor("white", 10) // color, transparency
	annot.setSize(30)
}

// Display scene
viewer.show()
viewer.setAnimationEnabled(true)
print("... Done. With Viewer active, Press 'H' or 'F1' for help")


/** 
 * Extracts all the branch points (BPs) of the specified cell that are
 * contained by {@code thalamus}. Does nothing if cell's soma is not
 * contained by {@code somaCompartment}
 */
def getFilteredBranchPoints(loader) {

	if (!somaCompartment || !thalamus) {
		println("Aborting: Compartment(s) are not valid")
		return null
	}
	if (!loader.idExists()) {
		println(" id not found. Skipping...")
		return null
	}
	// Retrieve the 1st node of the soma and its annotated compartment
	soma = loader.getNodes("soma")[0]
	compartment = (AllenCompartment) soma.getAnnotation()
	if (!somaCompartment.contains(compartment)) {
		println(" Soma not associated with " + somaCompartment + ". Skipping...")
		return null
	}
	println(" Id matches soma location requirements!")

	// Retrieve the axonal arbor as a Tree object. Instantiate a TreeAnalyzer
	// so that we can conveniently 1) access all of the axonal branch points,
	// and 2) filter them by annotated compartment
	axonalTree = loader.getTree("axon")
	analyzer = new TreeAnalyzer(axonalTree)
	branchPoints = analyzer.getBranchPoints(thalamus)
	println(" Found ${branchPoints.size()} match(es)")

	// return the filtered list of branch points
	branchPoints
}

