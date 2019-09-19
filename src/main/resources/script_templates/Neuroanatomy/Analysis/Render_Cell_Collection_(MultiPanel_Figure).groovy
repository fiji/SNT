#@File(style="directory", required=false, label="Reconstructions directory (Leave empty for demo):") recDir
#@String(label="Color mapping:", choices={"Ice.lut", "mpl-viridis.lut"}) lutName
#@LUTService lut
#@SNTService snt

import sc.fiji.snt.Tree
import sc.fiji.snt.analysis.MultiTreeColorMapper
import sc.fiji.snt.viewer.MultiViewer2D


// Retrive all reconstruction files from the directory
trees = Tree.listFromDir(recDir)
if (trees.isEmpty()) {
	// Directory is invalid. Let's retrieve demo data instead
	trees = snt.demoTrees()
	// Rotate the reconstructions to "straighten up" the
	// apical shaft of these pyramidal cell dendrites
	for (tree in trees) tree.rotate(Tree.Z_AXIS, 210)
}

// Align reconstructions, bringing their somas to a common plane
for (tree in trees) {
	root = tree.getRoot() // We could also use tree.getSomaLocation()
	tree.translate(-root.getX(), -root.getY(), -root.getZ())
}

// Color code each cell and assign a hue ramp to the group
colorTable = lut.loadLUT(lut.findLUTs().get(lutName))
mapper = new MultiTreeColorMapper(trees)
mapper.map(MultiTreeColorMapper.TOTAL_LENGTH, colorTable)

// Assemble a multi-panel Viewer2D from the color mapper
viewer = mapper.getMultiViewer()

// Customize viewer
viewer.setLayoutColumns(trees.size())
viewer.setGridlinesVisible(false)
viewer.setOutlineVisible(false)
viewer.setAxesVisible(false)

// Display result
viewer.show()