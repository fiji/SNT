[![](https://travis-ci.org/fiji/Simple_Neurite_Tracer.svg?branch=master)](https://travis-ci.org/fiji/Simple_Neurite_Tracer)

## Simple Neurite Tracer

The [ImageJ](http://imagej.net/) framework for semi-automated tracing of neurons
and other tube-like structures. It is part of the [Fiji distribution](http://imagej.net/Fiji)
of ImageJ. For details, please see http://imagej.net/SNT


## Development
SNT is currently under heavy development. Because the ongoing work does not guaranty full backwards compatibility with previous versions, the latest pre-releases are not yet made available through Fiji's main update site but through the [Neuroanatomy](http://imagej.net/Neuroanatomy) update site.

## Recent Capabilities of SNT (Scijava branch)

### Tracing:

* Support for multidimensional images (including multichannel, and those with a time axis)
* A new option to perform background tracing on a second, non-displayed image
* Manual Mode: Semi-automated tracing can be temporary disabled
* Pause mode: Tracing can be interleaved with image processing routines
* Edit mode: Paths can now be edited, i.e., a path can be merged into a existing one, or split into two. Nodes can be moved, deleted, or inserted.
* Preliminary support for sub-pixel accuracy,
* New GUI with extensive options and shortcuts


### Analysis:

* Paths can tagged, searched and filtered. Tags can be SWC fields, branch order labels, or a list of arbitrary strings. Analysis can be performed on all paths, or filtered subsets
* Paths can be color-coded by their properties: e.g., compartment, length, radii or depth
* Paths can be converted to IJ ROIs
* Masks can be generated from filled traces in a more efficient way
* Distribution command retrieves Histogram and Descriptive statistics of selected path properties
* Quick statistics command calculates common morphometric properties of traced arbors
* Detailed Horton-Strahler analysis (tabular and plotted results)
* Traces can be rendered in a rotating plot and exported as SVG/PDF


### To Document:
* ITK bridge
* Scripting
* Multi-threading improvements
* 3D Viewer legacy support
