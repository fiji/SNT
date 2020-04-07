import sc.fiji.snt.io.MouseLightLoader
import sc.fiji.snt.io.MouseLightQuerier
import sc.fiji.snt.annotation.AllenUtils


/**
 * Streamlined Example for bulk downloads from the MouseLight database. For
 * a more detailed approach have a look at Download_ML_Data_I.groovy. For
 * advanced queries, have a look at MouseLightQuerier
 * TF 20200326
 */

outDir = System.properties.'user.home' + '/Desktop/ML-neurons'
somaLoc = AllenUtils.getCompartment("CA3")

if (MouseLightLoader.isDatabaseAvailable()) {
	for (id in MouseLightQuerier.getIDs(somaLoc)) {
		loader = new MouseLightLoader(id)
		loader.save(outDir)
	}
}

