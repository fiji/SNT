import sc.fiji.snt.io.MouseLightLoader
import sc.fiji.snt.annotation.AllenCompartment
import sc.fiji.snt.annotation.AllenUtils


/**
 * Exemplifies how to programmatically retrieve data from MouseLight's database at
 * ml-neuronbrowser.janelia.org: It iterates through all the neurons in the server
 * and downloads data (both JSON and SWC formats) for cells with soma associated
 * with the specified brain area. Downloaded files will contain all metadata
 * associated with the cell (i.e., labeling used, strain, etc.)
 * For advanced queries, have a look at MouseLightQuerier
 * TF 20190317
 */

// Absolute path to saving directory. Will be created as needed
destinationDirectory = System.properties.'user.home' + '/Desktop/ML-neurons'

// The name of the brain compartment (Allen CCF ontology, as displayed by Reconstruction
// Viewer's CCF Navigator) NB: Specifying "Whole Brain" would effectively download
// _all_ reconstructions from the database
compartmentOfInterest = AllenUtils.getCompartment("CA3")

if (!MouseLightLoader.isDatabaseAvailable() || !compartmentOfInterest) {
    println("""Aborting: Can only proceed with valid compartment and
               successful connection to database""")
    return
}

nNeurons = MouseLightLoader.getNeuronCount()
println("ML Database is online with " + nNeurons + " available. Gathering IDs...")
ids = MouseLightLoader.getRangeOfIDs(1, nNeurons);

for (id in ids) {

	print("Parsing " + id + "...")
    loader = new MouseLightLoader(id)
    if (!loader.idExists()) {
        println(" Somehow neuron is not available? Skipping...")
        continue
    }

    somaCompartment = loader.getSomaCompartment()
    if (compartmentOfInterest.equals(somaCompartment) || compartmentOfInterest.isParentOf(somaCompartment)) {
        print(" Downloading: \tJSON saved: " + loader.saveAsJSON(destinationDirectory))
        println("\tSWC saved: " + loader.saveAsSWC(destinationDirectory))
    } else {
        println(" Soma not associated with " + compartmentOfInterest + ". Skipping...")
    }

}
println("Finished parsing of all " + ids.size() + "/" + nNeurons + " available neurons")
