# @Context context
# @LegacyService ls
# @DatasetService ds
# @DisplayService display
# @LogService log
# @SNTService snt
# @StatusService status
# @UIService ui
# @LUTService lut

'''
file:       Reconstruction_Viewer_Demo.py
author:     Tiago Ferreira
version:    20181010
info:       Exemplifies how to render a file in Reconstruction Viewer
'''
from tracing import Tree
from tracing.io import MLJSONLoader
from tracing.analysis import TreeColorizer
from tracing.plot import TreePlot3D

def run():

    # Import some data from the MouseLight database in 'headless' mode
    print("    Retriving ML neuron...")
    loader = MLJSONLoader("AA0100") # one of the largest cells in the database
    if not loader.isDatabaseAvailable():
        ui.showDialog("Could not connect to ML database", "Error")
        return
    if not loader.idExists():
        ui.showDialog("Somewhow the specified id was not found", "Error")
        return

    print("... Done. Assembling Tree...")
    tree = loader.getTree('all')

    # Define and assign ColorTable
    print("... Done. Assigning LUT to Tree...")
    colorizer = TreeColorizer(context)
    color_table = lut.loadLUT(lut.findLUTs().get("Ice.lut"))
    colorizer.colorize(tree, "branch order", color_table)

    # Visualize tree
    print("... Done. Preparing Reconstruction Viewer...")
    plot = TreePlot3D()
    plot.add(tree)
    bounds = colorizer.getMinMax()
    plot.addColorBarLegend(color_table, bounds[0], bounds[1])
    plot.show() 
    print("... Done. With Viewer active, Press 'H' or 'F1' for help")


run()
