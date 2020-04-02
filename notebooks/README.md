# SNT Notebooks

This collection of notebooks demonstrates how to access the [SNT] [] [API] from a
Python environment.


## Download
To run these notebooks from your local machine, download all the files as a
[ZIP archive](https://kinolien.github.io/gitzip/?download=https://github.com/morphonets/SNT/tree/master/notebooks)
and unzip its contents to a local directory. Alternatively, download the files
manually from [GitHub](https://github.com/morphonets/SNT/tree/master/notebooks).


## Requirements
The core requirement are [pyimagej] and [Fiji](https://imagej.net/Fiji).

### To Install Fiji:

1. [Download](https://imagej.net/Fiji/Downloads) the program.

### To install pyimagej:

1. Install [conda](https://www.anaconda.com/distribution/). See e.g., [here][pyimagej]
   for details.

2. Activate the [conda-forge](https://conda-forge.org/) channel:

  ```bash
  conda config --add channels conda-forge
  ```

3. Install [pyimagej] into a new conda environment named `pyimagej`:

  ```bash
  conda create -n pyimagej pyimagej openjdk=8
  ```

  At this point, it is convenient to make the new `pyimagej` environment available
  in the graphical notebook interface:

  ```bash
  conda install -n pyimagej ipykernel
  conda activate pyimagej
  python -m ipykernel install --user --name=pyimagej
  ```

  Now, when you now start jupyter, it will show `pyimagej` in the list of
  registered kernels. Selecting it, makes all the packages installed in the
  `pyimagej` environment available.


## Setup
Before running the notebooks, there are three more things to take care of:

1. Make sure your Fij installation is up-to-date and subscribed to the
   [NeuroAnatomy update site](https://imagej.net/SNT#install)

2. Make the notebooks aware of your local `Fij.app` location. You can do so by
   editing [ijfinder.py](./ijfinder.py):

  ```python
  local_fiji_dir = r'/path/to/your/local/Fiji.app'
  ```
  (replacing `/path/to/your/local/Fiji.app` with the actual path to `Fiji.app`).
  If you skip this step, you will be prompted to choose the Fiji directory
  every-time `ijfinder.py` is called.

3. If you haven't done so, install the packages required to run these notebooks
   in the `pyimagej` environment. The majority requires popular packages
   available from the default anaconda channel, e.g.:

  ```bash
  conda activate pyimagej
  conda install -c defaults matplotlib pandas seaborn scikit-learn
  ```

  However, some notebooks require specialized packages from `conda-forge`:

  ```bash
  conda install -c conda-forge gudhi trimesh
  ```
  Some functionality may require [blender](https://www.blender.org/download/).


## Running

Activate the `pyimagej` environment (if you have not registered it in `ipykernel`)
and start jupyter from the _notebooks_ [directory](./):

```bash
cd /path/to/notebooks/directory
conda activate pyimagej
jupyter notebook
```

(replacing `/path/to/notebooks/directory` with the path to the actual directory
where you unzipped the _notebooks_ directory). If you prefer JupyterLab, replace
`jupyter notebook` with `jupyter lab`.


## Troubleshooting
Installing packages from multiple channels may lead to installation conflicts.
Packages served by e.g., `conda-forge` and the regular `defaults` channel may
not be 1000% compatible. You can impose a preference for `conda-forge` by having
it listed on the top of your `.condarc` file, and by specifying the priority
policy:

```bash
conda config --add channels conda-forge
conda config --set channel_priority strict
```

You can also use the `-c` flag to specify a package from a specific channel.
E.g., You can install matplotlib from the `defaults` channel:

```bash
conda activate pyimagej
conda install -c defaults matplotlib
```
or from `conda-forge`:

```bash
conda activate pyimagej
conda install -c conda-forge matplotlib
```

## Resources
- SNT:
 - [Documentation][snt]
 - [API]
 - [Repository](https://github.com/morphonets/SNT)
 - [Image.sc](https://forum.image.sc/tag/snt/)

- pyimagej:
 - [Documentation][pyimagej]
 - [Repository](https://github.com/imagej/pyimagej)
 - [Image.sc](https://forum.image.sc/tag/pyimagej/)

- conda:
 - [Documentation](https://docs.conda.io/projects/conda/en/latest/)


[snt]: https://imagej.net/SNT
[api]: https://morphonets.github.io/SNT
[pyimagej]: https://github.com/imagej/pyimagej
