import tracing.SNT;
import tracing.io.MouseLightLoader;
import java.text.DecimalFormat;

/** 
 * Exemplifies how to download data from MouseLight's database at ml-neuronbrowser.janelia.org.
 * TF 20190314
 */

// Absolute path to saving directory
destinationDirectory = System.properties.'user.home' + "/Desktop/ML-neurons/"

// Range of IDs to be downloaded
idRange = 1..100

// Log IOExceptions to console?
debug = false

if (!MouseLightLoader.isDatabaseAvailable()) {
	println("Aborting: Database cannot be reached! Please check your connection...")
	return;
}
println("ML Database is online")
SNT.setDebugMode(debug)
for (index in idRange) {
	id = "AA" + new DecimalFormat("0000").format(index)
	print("Downloading " + id + "...")
	loader = new MouseLightLoader(id)
	if (!loader.idExists()) {
		println(" id not found. Skipping...")
		continue
	}
	print("\tJSON saved: " + MouseLightLoader.saveAsJSON(id, destinationDirectory))
	println("\tSWC saved: " + MouseLightLoader.saveAsSWC(id, destinationDirectory))
}
println("Downloads complete.")