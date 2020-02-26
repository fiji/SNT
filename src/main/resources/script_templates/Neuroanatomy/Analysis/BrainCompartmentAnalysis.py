"""
file:       Brain_Comparment_Analysis.py
author:     Tiago Ferreira
version:    20200126
info:       A Python demo on how to assess the brain areas a neuron projects to
            (in this case a MouseLight neuron retrived through direct connection
            to the MouseLight database (internet connection required). 
"""
from sc.fiji.snt.analysis import (NodeStatistics, TreeAnalyzer, TreeStatistics)
from sc.fiji.snt.annotation import AllenUtils
from sc.fiji.snt.io import MouseLightLoader


# In this example we are going to analyze the axonal projections of a Mouselight
# neuron across the whole brain (Allen CCF). For simplicity, we'll focus only on
# brain regions defined by mid level ontologies (as per Reconstruction Viewer's 
# Allen CCF navigator):
max_ontology_depth = 6

# Let's retrive the axonal arbor of cell id AA0788
loader = MouseLightLoader("AA0788")
soma_loc = loader.getSomaLocation()
axon = loader.getTree("axon")

# Let's analyze the axonal terminals: What's their whole-brain distribution?
analyzer = TreeAnalyzer(axon)
tips = analyzer.getTips()
nStats = NodeStatistics(tips)
hist = nStats.getBrainAnnotationHistogram(max_ontology_depth)
hist.annotate('No. of tips: {}'.format(tips.size()))
hist.show()

# Does this neuron project to contra-lateral locations?
hist = nStats.getHistogram("x coordinates") # Medial-Lateral axis
midline = AllenUtils.brainCenter().getX()
hist.annotateXline(midline, "midline")
hemisphere = "left" if AllenUtils.isLeftHemisphere(soma_loc) else "right" 
hist.annotatePoint(soma_loc.getX(), 0, "soma ({} hemi.)".format(hemisphere))
hist.show()

# Does the distribution of axonal cable reflect the distribution of tips?
tStats = TreeStatistics(axon);
hist = tStats.getBrainAnnotationHistogram(max_ontology_depth)
hist.annotate('Soma location: {}'.format(soma_loc.getAnnotation().acronym()))
hist.show()

