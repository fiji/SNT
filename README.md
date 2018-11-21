[![](https://travis-ci.org/fiji/Simple_Neurite_Tracer.svg?branch=master)](https://travis-ci.org/fiji/Simple_Neurite_Tracer)

## Simple Neurite Tracer

The [ImageJ](http://imagej.net/) framework for semi-automated tracing of neurons
and other tube-like structures. It is part of the [Fiji distribution](http://imagej.net/Fiji)
of ImageJ. For details, please see http://imagej.net/Simple_Neurite_Tracer


## Development
SNT is currently under heavy development. Because the ongoing work does not guaranty full backwards compatibility with previous versions, the latest pre-releases are not yet made available through Fiji's main update site but through the [Neuroanatomy](http://imagej.net/Neuroanatomy) update site.

## SNT Capabilities ([scijava](https://github.com/fiji/Simple_Neurite_Tracer/tree/scijava) branch)

### Tracing
* Support for multidimensional images (including multichannel, and those with a time axis)
* A* search can be performed on a second, non-displayed image. This allows for e.g., tracing on a pre-process (filtered) image while interacting with the unfiltered image (or vice-versa). If enough RAM is available toggling between the two images is immediate.
* Manual Mode: Semi-automated tracing can be temporary disabled
* Pause mode: Tracing can be interleaved with image processing routines
* Edit mode: Paths can now be edited, i.e., a path can be merged into a existing one, or split into two. Nodes can be moved, deleted, or inserted.
* Preliminary support for sub-pixel accuracy,

### Analysis
* Paths can tagged, searched and filtered. Tags can be SWC fields, branch order labels, or a list of arbitrary strings. Analysis can be performed on all paths, or filtered subsets
* Direct access to major public databases, including [MouseLight](https://ml-neuronbrowser.janelia.org/), [FlyCircuit](http://www.flycircuit.tw) and [NeuroMorpho](http://neuromorpho.org/)
* Paths can be color-coded by morphometric properties: e.g., compartment, length, radii or depth
* Paths can be converted to IJ ROIs
* Distribution command retrieves Histogram and Descriptive statistics of morphometric properties
* Quick statistics command calculates common morphometric properties of traced arbors
* Detailed Sholl and Horton-Strahler analyses
* Traces can be rendered in a rotating plot and exported as SVG/PDF
* Batch analysis of multiple files

## Scripting
* `SNTService`: Programmatic access to the full SNT API *during* an interactive tracing session. `SNTService` allows the blending of automated workflows with manual curation steps
* It is now possible to run scripts (in any of the IJ2 supported languages) directly from within SNT. See examples on either the _Templates_ menu of the Script Editor and the _Scripts_ menu of SNT

## Performance
* Multi-threading improvements
* Migration to Scijava allows several commands to run headless
* Masks can be generated from filled traces in a more efficient way
* Tabbed GUI with extensive options and shortcuts

## Reconstruction Viewer
* The Reconstruction Viewer is a powerful OpenGL visualization tool for both meshes and reconstructions
* It can be called both as a standalone program or from withing SNT
* Replaces most of the funtionality of the outdated legacy 3D Viewer, with extended features:
  * Advanved rendering supporting axes, transparency, color interpolation and path smoothing
  * Interactive scenes (controled rotations, paning, zoom, scaling, animation, "dark"/"light" mode)
  * Tools for management and customization of scene elements
  * Ability to render both local and remote files on th same scene
  * Loading of surface meshes of several template brains (Drosophila and Allen CCF (Allen Mouse Brain Atlas))
  * Colormappers for morphometric properties


### To Document:
* ITK bridge
