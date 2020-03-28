# SNT Notebooks

Welcome to the collection of [SNT] notebooks that demonstrate how to access its
[API] from a Python environment.

## Download
To run these notebooks from your local machine download all the files as a
[ZIP archive](https://kinolien.github.io/gitzip/?download=https://github.com/morphonets/SNT/tree/master/notebooks&token=f523985da9454712fb16f9ef39e9de65015af4aa),
and unzip the files to a local directory. Alternatively, download them manually
from [GitHub](https://github.com/morphonets/SNT/tree/master/notebooks).

## Requirements
These notebooks require you to have installed [pyimagej]. The easiest way to do
so is to:

1. Install [conda](https://www.anaconda.com/distribution/)
2. Install [pyimagej] on a dedicated environment:
 
  - Activate the [conda-forge](https://conda-forge.org/) channel:

  ```
  conda config --add channels conda-forge
  conda config --set channel_priority strict
  ```
  - Install [pyimagej] into a new conda environment:

  ```
  conda create -n pyimagej pyimagej openjdk=8
  ```

## Running

  ```
  cd /path/to/notebooks/directory
  conda activate pyimagej
  jupyter notebook      (or jupyter lab)
  ```

## Troubleshooting
If is fairly easy to hit installations conflicts when installing bleeding-edge
packages relying on complex dependencies and libraries (such as pyimagej).
conda-forge _usually_ provides more recent building recipes that can be used to
solve such conflicts. E.g., To install [pandas](https://pandas.pydata.org/),
rather than using:

```
conda activate pyimagej
conda install pandas
```

Use:

```
conda activate pyimagej
conda install -c conda-forge pandas
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

[snt]: https://imagej.net/SNT
[api]: https://morphonets.github.io/SNT
[pyimagej]: https://pypi.org/project/pyimagej/
