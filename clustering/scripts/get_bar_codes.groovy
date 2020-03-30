import sc.fiji.snt.*
import sc.fiji.snt.analysis.*

cellsDir = "/home/tferr/code/morphonets/SNT/clustering/zi/cells"
outDir = "/home/tferr/code/morphonets/SNT/clustering/zi"

println("Retrieving axons...")
trees = Tree.listFromDir(cellsDir, "", "axon")
for (filter in ["radial", "geodesic"]) {
	println("Generating " + filter + " barcodes...")
	map = [:]
	for (tree in trees) {
		analyzer = new PersistenceAnalyzer(tree)
		map[tree.getLabel()] = analyzer.getBarCodes(filter)
	}
	saveMap(map, outDir + "/barcodes-" + filter + ".csv")
}

def saveMap(map, outpath) {

	// retrieve the size of the smallest array in map
	lastIdx = map.min{ it.value.size() }.value.size()

	// dump everything in table
	table = new SNTTable()
	map.each { key, list ->
		row = table.insertRow(key)
		for (i in 0..<lastIdx) {
			table.set("Idx" + i, row, list.get(i))
		}
	}
	saved = table.save(outpath)
	println("Data saved (" + lastIdx + " cols): " + saved)
}
