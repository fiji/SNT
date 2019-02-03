import imagej
import numpy as np
import ast
from scipy.spatial import ConvexHull

ij = imagej.init(r'C:\Users\cam\Desktop\Fiji.app', headless=False)

plugin = "Simple Neurite Tracer..."
args = {'image': 'None. Run SNT in Analysis Mode'}
ij.py.run_plugin(plugin)

script = """
# @Context context
# @LegacyService ls
# @DatasetService ds
# @DisplayService display
# @LogService log
# @SNTService snt
# @StatusService status
# @UIService ui
# @output String dendrite_tip_list
# @output String axon_tip_list


from tracing import (Path, PathAndFillManager, SimpleNeuriteTracer, SNTUI, Tree)
from tracing.io import MLJSONLoader
from tracing.util import PointInImage
from tracing.analysis import (RoiConverter, TreeAnalyzer, TreeColorMapper, 
    TreeStatistics)
from tracing.viewer import(Viewer2D, Viewer3D)


def run():

    # Import some data from the MouseLight database in 'headless' mode
    loader = MLJSONLoader("AA0100") # one of the largest cells in the database
    if not loader.isDatabaseAvailable():
        ui.showDialog("Could not connect to ML database", "Error")
        return
    if not loader.idExists():
        ui.showDialog("Somewhow the specified id was not found", "Error")
        return

    d_tree = loader.getTree('dendrites', None)  # compartment, color
    a_tree = loader.getTree('axon', None)
       
    dendrite_tip_positions = TreeAnalyzer(d_tree).getTips()
    dendrite_tip_list = [[tip.x, tip.y, tip.z] for tip in dendrite_tip_positions]
    
    axon_tip_positions = TreeAnalyzer(a_tree).getTips()
    axon_tip_list = [[tip.x, tip.y, tip.z] for tip in axon_tip_positions]
    
    return axon_tip_list, dendrite_tip_list
    
axon_tip_list, dendrite_tip_list = run()
"""

language_extension = 'py'
result = ij.py.run_script(language_extension, script)
dendrite_tip_list = ast.literal_eval(result.getOutput('dendrite_tip_list'))
axon_tip_list = ast.literal_eval(result.getOutput('axon_tip_list'))

print(len(axon_tip_list))
print(len(dendrite_tip_list))
d_hull = ConvexHull(dendrite_tip_list)
print("The volume of the convex hull encapsulating \
all dendritic end points is ~{} cubic micrometers".format(int(d_hull.volume**(1/3))))
