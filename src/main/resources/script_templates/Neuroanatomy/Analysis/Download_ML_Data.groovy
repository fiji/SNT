import sc.fiji.snt.io.MouseLightLoader
import sc.fiji.snt.annotation.AllenCompartment
import sc.fiji.snt.annotation.AllenUtils
import java.text.DecimalFormat

/**
 * Exemplifies how to programmatically retrieve data from MouseLight's database at
 * ml-neuronbrowser.janelia.org: It iterates through all the neurons in the server
 * and downloads data (both JSON and SWC formats) for cells with soma associated
 * with the specified Allen Reference Atlas (ARA) compartment. Downloaded files will
 * contain all metadata associated with the cell (i.e., labeling used, strain, etc.)
 * For advanced queries, have a look at MouseLightQuerier
 * TF 20190317
 */

// Absolute path to saving directory. Will be created as needed
destinationDirectory = System.properties.'user.home' + '/Desktop/ML-neurons'

// The name of the ARA compartment to be screened (as displayed by Reconstruction
// Viewer's CCF Navigator) NB: Specifying "Whole Brain" would effectively download
// _all_ reconstructions from the database
compartmentOfInterest = AllenUtils.getCompartment("CA3")

if (!MouseLightLoader.isDatabaseAvailable() || !compartmentOfInterest) {
    println("""Aborting: Can only proceed with valid compartment and
               successful connection to database""")
    return
}

println("ML Database is online")
for (id in 1..MouseLightLoader.getNeuronCount()) {

    // Define a valid cell ID (We could also use MouseLightLoader#getAllNeuronIDs)
    id = "AA" + new DecimalFormat("0000").format(neuron)
    loader = loader = new MouseLightLoader(id)
    print("Parsing " + id + "...")
    if (!loader.idExists()) {
        println(" id not found. Skipping...")
        continue
    }

    // Retrieve the 1st node of the soma and its annotated compartment
    soma = loader.getNodes("soma")[0]
    somaCompartment = (AllenCompartment) soma.getAnnotation()

    if (compartmentOfInterest.contains(somaCompartment)) {
        print(" Downloading: \tJSON saved: " + loader.saveAsJSON(destinationDirectory))
        println("\tSWC saved: " + loader.saveAsSWC(destinationDirectory))
    } else {
        println(" Soma not associated with " + compartmentOfInterest + ". Skipping...")
    }

}
println("Finished parsing of all " + MouseLightLoader.getNeuronCount() + " available neurons")