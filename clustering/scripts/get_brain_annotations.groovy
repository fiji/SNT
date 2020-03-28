import sc.fiji.snt.*
import sc.fiji.snt.analysis.*
import sc.fiji.snt.annotation.*

cellsDir = "/home/tferr/code/morphonets/SNT/clustering/zi/cells"
outDir = "/home/tferr/code/morphonets/SNT/clustering/zi"
maxOntLevel = (int) Math.round(AllenUtils.getHighestOntologyDepth()/2)

println("Retrieving axons... Max. ontology level: " + maxOntLevel)
trees = Tree.listFromDir(cellsDir, "", "axon")

lTable = new SNTTable()
tTable = new SNTTable()

for (tree in trees) {

	println("Parsing " + tree.getLabel())
	lTable.appendRow(tree.getLabel())
	tTable.appendRow(tree.getLabel())
	row = Math.max(0, lTable.getRowCount() - 1)

	tStats = new TreeStatistics(tree)
	tStats.getAnnotatedLength(maxOntLevel).each{ annot, length ->
		colHeader = (annot == null) ? "Length:other" : "Length:" + annot.acronym()
		lTable.set(colHeader, row, length);
	}

	nStats = new NodeStatistics(tStats.getTips())
	nStats.getBrainAnnotations(maxOntLevel).each{annot, tipsCount ->
		colHeader = (annot == null) ? "Tips:other" : "Tips:"+annot.acronym()
		tTable.set(colHeader, row, tipsCount)
	}

}
lTable.fillEmptyCells(0)
saved = lTable.save(outDir + "/brain-annot-length-maxLevel" + maxOntLevel + ".csv")
println("lTable saved: " + saved)

tTable.fillEmptyCells(0)
saved = tTable.save(outDir + "/brain-annot-tips-maxLevel" + maxOntLevel + ".csv")
println("tTable saved: " + saved)
