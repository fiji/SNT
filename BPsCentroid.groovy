# @ImageJ ij

import java.text.DecimalFormat
import sc.fiji.snt.*
import sc.fiji.snt.io.*
import sc.fiji.snt.analysis.*
import sc.fiji.snt.annotation.*
import sc.fiji.snt.viewer.*
import sc.fiji.snt.util.*
import org.scijava.util.*

somaCompartment = AllenUtils.getCompartment("Somatomotor areas")
thalamus = AllenUtils.getCompartment("Thalamus")

class Centroid {
    SNTPoint soma
    SNTPoint bps
    String label
}

def getCentroids(cellId) {

    // Retrieve the 1st node of the soma and its annotated compartment
    soma = loader.getNodes("soma")[0]
    compartment = (AllenCompartment) soma.getAnnotation()
    if (compartment && !somaCompartment.contains(compartment)) {
        println(" Soma not associated with " + somaCompartment + ". Skipping")
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
    println(" Assessing location of ${branchPoints.size()} branch points...")
    filteredBranchPoints = []
    for (branchPoint in branchPoints) {
        compartment = (AllenCompartment) branchPoint.getAnnotation()
        if (compartment && thalamus.contains(compartment))
            filteredBranchPoints.add(branchPoint)
    }
    println(" Found ${filteredBranchPoints.size()} match(es)")

    if (filteredBranchPoints.isEmpty())
    	return null

    centroid = new Centroid()
    centroid.soma = soma
    centroid.bps = SNTPoint.average(filteredBranchPoints)
    centroid.label = id

    centroid
}

nNeurons = 10//MouseLightLoader.getNeuronCount()
centroidList = []
for (neuron in 590..600) {

    // Define a valid cell ID
    id = "AA" + new DecimalFormat("0000").format(neuron)
    loader = loader = new MouseLightLoader(id)
    print("Parsing " + id + "...")
    if (!loader.idExists()) {
        println(" id not found. Skipping...")
        continue
    }

    centroid = getCentroids(id)
    if (!centroid) {
        println(" Bummer! No branch points are associated with $thalamus")
    } else {
        centroidList << centroid
    }
}
println("Finished parsing of all " + MouseLightLoader.getNeuronCount() + " available neurons")
if (centroidList.isEmpty()) return

viewer = new Viewer3D(ij.context())
colors = SNTColor.getDistinctColors(centroidList.size())

centroidList.eachWithIndex { centroid, index ->
    annot = viewer.annotateLine([centroid.soma, centroid.bps], centroid.label)
    annot.setSize(30)
    annot.setColor(colors[index], 10)
}

// Add meshes
brainMesh = AllenUtils.getCompartment("Whole Brain").getMesh()
viewer.add(brainMesh)
viewer.add(somaCompartment.getMesh())
viewer.add(thalamus.getMesh())

viewer.show()