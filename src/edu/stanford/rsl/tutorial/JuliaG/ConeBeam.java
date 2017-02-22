package edu.stanford.rsl.tutorial.JuliaG;

import edu.stanford.rsl.conrad.filtering.ImageFilteringTool;
import edu.stanford.rsl.conrad.filtering.redundancy.TrajectoryParkerWeightingTool;
import edu.stanford.rsl.apps.gui.ReconstructionPipelineFrame;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import edu.stanford.rsl.conrad.data.numeric.Grid2D;
import edu.stanford.rsl.conrad.data.numeric.Grid3D;
import edu.stanford.rsl.conrad.utils.CONRAD;
import edu.stanford.rsl.conrad.utils.Configuration;
import edu.stanford.rsl.conrad.utils.ImageUtil;
import ij.IJ;
import ij.ImageJ;

public class ConeBeam {
	public static void main (String [] args) throws Exception{
		CONRAD.setup();
		Configuration con = Configuration.getGlobalConfiguration();
		//Load and visualize the projection image data
		String filename = "/proj/i5fpctr/data/Katrin/XCatDynamicSquat_NoTruncation_256proj_620_480_MeadianRad2_80keVnoNoise.tif";
		Grid3D image = ImageUtil.wrapImagePlus(IJ.openImage(filename));
		image.show("Knee");
		Grid3D filtered = new Grid3D (image);
			
		TrajectoryParkerWeightingTool pwt = new TrajectoryParkerWeightingTool();
		pwt.configure();
		
		//for (int i=0; i<filtered.getSize()[2]; i++){
		//	Grid2D parkerweight = filtered.getSubGrid(i);
		//	parkerweight = pwt.applyToolToImage(parkerweight);
		//	filtered.setSubGrid(i, parkerweight);
		//}
		
		
		
		ImageFilteringTool[] filters = new ImageFilteringTool[] {pwt};
		ImageUtil.applyFiltersInParallel(filtered, filters);
		filtered.show();
	}
}
