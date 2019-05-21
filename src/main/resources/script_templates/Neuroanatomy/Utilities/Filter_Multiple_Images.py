# @ImageJ ij
# @String(value="This script processes all TIFF images in a directory with the Frangi Vesselness filter. Conversion log is shown in Console.", visibility="MESSAGE") msg
# @File(label="Directory containing your images", style="directory") input_dir
# @String(label="Consider only filenames containing",description="Clear field for no filtering",value="") name_filter
# @boolean(label="Include subdirectories") recursive
# @float(label="Size of structures to be filtered (spatially calibrated units)") scale
# @LogService log
# @StatusService status
# @UIService uiservice

"""
file: Filter_Multiple_Images.py
author: Tiago Ferreira, Cameron Arshadi
info: Bulk filtering of image files using Frangi Vesselness
"""

import os


def get_image_files(directory, filtering_string, extension):
    """Returns a list containing the paths of files in the specified
       directory. The list will only include files with the supplied
       extension whose filename contains the specified string."""
    files = []
    for (dirpath, dirnames, filenames) in os.walk(directory):
        for f in filenames:
            if os.path.basename(f).startswith('.'):
                continue
            if filtering_string in f and f.lower().endswith(extension):
                files.append(os.path.join(dirpath, f))
        if not recursive:
            break  # do not process subdirectories
            
    return files


def run():

    d = str(input_dir)
    extension = ".tif"
    files = get_image_files(d, name_filter, extension)
    if not files or len(files) == 0:
        uiservice.showDialog("No files matched the specified criteria", "Error")
        return

    for (counter, f) in enumerate(files):
    
        basename = os.path.basename(f)
        msg = 'Processing file %s: %s...' % (counter + 1, basename)
        status.showStatus(msg)
        log.info(msg)

        # Load the input image
        input_image = ij.io().open(f)
        # Convert input image to float, since we are dealing with derivatives
        float_input = ij.op().run("convert.float32", input_image)

        # Obtain spatial calibration of the image
        x_spacing = float_input.averageScale(0)
        y_spacing = float_input.averageScale(1)
        spacing = [x_spacing, y_spacing]
        dimensions = float_input.numDimensions()
        if dimensions > 2:
            z_spacing = float_input.averageScale(2)
            spacing.append(z_spacing)
        
        # Create placeholder image for the output then run the Frangi op
        output = ij.op().run("create.img", float_input)
        ij.op().run("frangiVesselness", output, float_input, spacing, scale)

        # Save the result using the same basename as the image, adding "[Frangi].tif"
        # For example, the output for "OP_1.tif" would be named "OP_1[Frangi].tif"
        l = len(f)
        el = len(extension)
        output_filepath = f[0:l-el] + "[Frangi].tif"
        ij.io().save(output, output_filepath)

    log.info('Done. %s file(s) processed...' % (counter + 1))
    
    
run()
