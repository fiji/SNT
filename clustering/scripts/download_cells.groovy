import sc.fiji.snt.io.MouseLightLoader
import sc.fiji.snt.annotation.AllenCompartment
import sc.fiji.snt.annotation.AllenUtils

outDir = "/home/tferr/code/morphonets/SNT/clustering/zi/cells"
somaLoc = AllenUtils.getCompartment("ZI")

for (id in MouseLightQuerier.getIDs(somaLoc)) {
	MouseLightLoader loader = new MouseLightLoader(id)
	loader.save(outDir)
}
