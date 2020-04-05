# @Context context

import os, sys
from sc.fiji.snt import Tree, SNTUtils
from sc.fiji.snt.analysis import TreeAnalyzer
from org.scijava.table import DefaultGenericTable
from java.io import File

in_dir = '/home/tferr/code/morphonets/SNT/clustering/zi/cells'
out_dir = '/home/tferr/code/morphonets/SNT/clustering/zi'

def run():

    metrics = TreeAnalyzer.getAllMetrics()
    metrics.remove('Assigned value')
    metrics.remove('No. of fitted paths')

    for st in ["axon", "dendr"]:

        table = DefaultGenericTable()
        csv_file = File('%s/analyzer-%s.csv' % (out_dir, st))

        for tree in Tree.listFromDir(in_dir):
            stree = tree.subTree(st)
            analyzer = TreeAnalyzer(stree)
            analyzer.setContext(context)
            analyzer.setTable(table, 'SWC Measurements')
            analyzer.measure(metrics, False)

        SNTUtils.saveTable(table, csv_file)

run()
