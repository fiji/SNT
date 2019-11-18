# @Context context
# @LUTService lut
# @SNTService snt
# @UIService ui

"""
file:       Reconstruction_Viewer_Demo.py
author:     Tiago Ferreira
version:    20190318
info:       Exemplifies how to render reconstructions and neuropil annotations
            in a stand-alone Reconstruction Viewer. Requires internet connection
"""
from sc.fiji.snt import Tree
from sc.fiji.snt.io import MouseLightLoader
from sc.fiji.snt.analysis import TreeColorMapper
from sc.fiji.snt.annotation import AllenCompartment
from sc.fiji.snt.annotation import AllenUtils
from sc.fiji.snt.viewer import Viewer3D
from sc.fiji.snt.viewer.Viewer3D import ViewMode

from org.scijava.util import Colors

def run():

    if not MouseLightLoader.isDatabaseAvailable():
        ui.showDialog("Could not connect to ML database", "Error")
        return

    # Import one of the largest cells in the MouseLight database
    loader = MouseLightLoader("AA0100")
    if not loader.idExists():
        ui.showDialog("Somehow the specified id was not found", "Error")
        return

    # We'll visualize dendrites and axons differently, loading cell in 2 steps
    print("    Retrieving ML neuron...")
    dend_tree = loader.getTree('dendrite', Colors.ORANGE)
    dend_tree.setLabel("AA0100 (Dendrites)")
    axon_tree = loader.getTree('axon', None)
    axon_tree.setLabel("AA0100 (Axon)")

    # Color code axons: map path order to color table
    print("... Done. Assigning LUT to axonal arbor...")
    mapper = TreeColorMapper(context)
    color_table = lut.loadLUT(lut.findLUTs().get("Ice.lut"))
    mapper.map(axon_tree, "path order", color_table)
    bounds = mapper.getMinMax()

    # Assemble 3D scene in Reconstruction Viewer
    print("... Done. Preparing Reconstruction Viewer...")
    viewer = snt.newRecViewer(True)  # viewer with GUI controls?

    # Add reconstructions and color map legend
    viewer.add(dend_tree)
    viewer.add(axon_tree)
    viewer.addColorBarLegend(color_table, bounds[0], bounds[1])

    # Add Allen Reference Atlas (ARA) annotations: brain contour
    # and compartment associated with soma of downloaded cell
    brainMesh = AllenUtils.getCompartment("Whole Brain").getMesh()
    brainMesh.setBoundingBoxColor(Colors.GRAY)
    viewer.add(brainMesh)
    soma_annotation = next(iter(loader.getNodes("soma"))).getAnnotation()
    viewer.add(soma_annotation.getMesh())

    # Display scene
    viewer.setViewMode(ViewMode.SIDE)
    viewer.show()
    viewer.setAnimationEnabled(True)
    print("... Done. With Viewer active, Press 'H' or 'F1' for help")
    print("    To programmatically access the Viewer at a later time point:")
    print("    viewer = snt.getRecViewer(%s)" % viewer.getID())


run()
