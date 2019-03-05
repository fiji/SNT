# @Context context
# @UIService ui
# @LUTService lut


"""
file:       Reconstruction_Viewer_Demo.py
author:     Tiago Ferreira
version:    20190218
info:       Exemplifies how to render a remote file in a stand-alone
            Reconstruction Viewer
"""
from tracing import Tree
from tracing.io import MouseLightLoader
from tracing.analysis import TreeColorMapper
from tracing.viewer import OBJMesh, Viewer3D
from tracing.viewer.Viewer3D import ViewMode
from org.scijava.util import Colors

def run():

    # Import some data from the MouseLight database
    print("    Retrieving ML neuron...")
    loader = MouseLightLoader("AA0100") # one of the largest cells in the database
    if not loader.isDatabaseAvailable():
        ui.showDialog("Could not connect to ML database", "Error")
        return
    if not loader.idExists():
        ui.showDialog("Somehow the specified id was not found", "Error")
        return

    print("... Done. Assembling Tree...")
    tree = loader.getTree('all', None)  # compartment, color

    # Define and map branch order to color table
    print("... Done. Assigning LUT to Tree...")
    mapper = TreeColorMapper(context)
    color_table = lut.loadLUT(lut.findLUTs().get("Ice.lut"))
    mapper.map(tree, "branch order", color_table)

    # Visualize tree in Reconstruction Viewer
    print("... Done. Preparing Reconstruction Viewer...")
    viewer = Viewer3D(context) # Initialize viewer with GUI controls
    viewer.add(tree)
    bounds = mapper.getMinMax()
    viewer.addColorBarLegend(color_table, bounds[0], bounds[1])
    brain = viewer.loadMouseRefBrain()
    brain.setBoundingBoxColor(Colors.GREEN)
    viewer.setViewMode(ViewMode.SIDE)
    viewer.show()
    viewer.setAnimationEnabled(True)
    print("... Done. With Viewer active, Press 'H' or 'F1' for help")


run()
