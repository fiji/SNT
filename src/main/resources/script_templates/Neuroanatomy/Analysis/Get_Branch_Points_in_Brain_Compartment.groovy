# @ImageJ ij
# @LUTService lut

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


// First we specify the compartments of the cells we want to filter
// (we'll use the name of the ARA compartments to be screened as
// displayed by Reconstruction Viewer's CCF Navigator)
somaCompartment = AllenUtils.getCompartment("Somatomotor areas")
thalamus = AllenUtils.getCompartment("Thalamus")

/** 
 * Extracts all the branch points (BPs) of the specified cell that are
 * contained by {@code thalamus}. Does nothing if cell's soma is not
 * contained by {@code somaCompartment}
 */
def getFilteredBranchPoints(cellId) {

	if (!somaCompartment || !thalamus) {
		println("Aborting: Compartment(s) are not valid")
		return null
	}
	loader = new MouseLightLoader(cellId)
	print("Parsing " + cellId + "...")
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
	// so that we can conveniently access all of the axonal branch points
	axonalTree = loader.getTree("axon")
	analyzer = new TreeAnalyzer(axonalTree)
	branchPoints = analyzer.getBranchPoints()

	// Iterate through all the branch points (PointInImage objects)
	// and extract those in thalamus
	print("Assessing location of ${branchPoints.size()} branch points...")
	filteredBranchPoints = []
	for (branchPoint in branchPoints) {
		compartment = (AllenCompartment) branchPoint.getAnnotation()
		if (compartment) //&& thalamus.contains(compartment))
			filteredBranchPoints.add(branchPoint);
	}
	println(" Found ${filteredBranchPoints.size()} match(es)")

	// return filtered list of branch points
	filteredBranchPoints
}

println("Analysis settings are: ")
println(" Soma compartment: $somaCompartment")
println(" BPs compartment: $thalamus")

// In this demo we'll just parse a single cell. Have a look at
// Download_ML_Data.groovy for an example on how to parse all
// neurons in the ML database
id = "AA0596"
bps = getFilteredBranchPoints(id)
if (!bps) {
  println(" Bummer! No branch points are associated with $thalamus")
  return
}

// Let's look at the centroid of the extracted nodes
bpsCentroid = SNTPoint.average(bps)
somaNodes = new MouseLightLoader(id).getNodes("soma")
somaCentroid = SNTPoint.average(somaNodes)
println("SomaLocation: $somaCentroid.x, $somaCentroid.y, $somaCentroid.z")
println("Centroid of all BPs in ${thalamus.name()}: $bpsCentroid.x, $bpsCentroid.y, $bpsCentroid.z")

// Which branches are associated with these BPs? Terminal? Secondary?
// Let's look at the Strahler distribuition. For convenience we'll
// assemble a Tree from such branches (Paths in SNT)
branches = []
for (bp in bps) { branches.add(bp.getPath()) }
bpsTree = new Tree(branches)
bpsTree.setLabel("BPs in ${thalamus.acronym()}")
bpsStats = new TreeStatistics(bpsTree)
bpsStats.getHistogram("branch order").show()

// What about their length?
bpsStats.getHistogram("length").show()

// Let's assemble a Reconstruction Viewer to visualize the data
viewer = new Viewer3D(ij.context())

// Add meshes
brainMesh = AllenUtils.getCompartment("Whole Brain").getMesh()
viewer.add(brainMesh)
viewer.add(somaCompartment.getMesh())
viewer.add(thalamus.getMesh())

// Add original cell
tree = loader.getTree()
tree.setColor("yellow")
viewer.add(tree)

// Annotate thalamic BPs color coded by depth
mapper = new TreeColorMapper(ij.context())
colorTable = lut.loadLUT(lut.findLUTs().get("Ice.lut"))
mapper.map(bpsTree, "z coordinates", colorTable)
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
