package edu.stanford.rsl.tutorial.JuliaG;

import ij.ImageJ;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLCommandQueue;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLImage2d;
import com.jogamp.opencl.CLImageFormat;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLProgram;
import com.jogamp.opencl.CLImageFormat.ChannelOrder;
import com.jogamp.opencl.CLImageFormat.ChannelType;
import com.jogamp.opencl.CLMemory.Mem;

import edu.stanford.rsl.tutorial.JuliaG.*;
import edu.stanford.rsl.tutorial.phantoms.SheppLogan;
import edu.stanford.rsl.tutorial.praktikum.Phantom;
import edu.stanford.rsl.conrad.data.generic.complex.OpenCLGridTest;
import edu.stanford.rsl.conrad.data.numeric.Grid2D;
import edu.stanford.rsl.conrad.data.numeric.opencl.OpenCLGrid1D;
import edu.stanford.rsl.conrad.data.numeric.opencl.OpenCLGrid2D;
import edu.stanford.rsl.conrad.data.numeric.opencl.OpenCLGridInterface;
import edu.stanford.rsl.conrad.opencl.OpenCLUtil;
  
public class BackprojectionOpenCL {
      
    public static Grid2D reconstructionCL(int worksize, int sizereconstruction, float spacing, float deltaS, float maxS, float maxTheta, float deltaTheta, Grid2D grid) {
    // create context
    CLContext context = OpenCLUtil.getStaticContext();
      
    //show OpenCL devices in System
    //CLDevice[] devices = context.getDevices();
     
    // create sinogram of grid
    Grid2D sinogram = new Grid2D(ParallelBeam.sinogram(grid, maxTheta, deltaTheta, maxS, deltaS));
    sinogram = ParallelBeam.ramlak(sinogram);
    sinogram.show("The filtered sinogram");
    
    // select device
    CLDevice device = context.getMaxFlopsDevice();
      
    int imageSize = grid.getSize()[0] * grid.getSize()[1]; // 1D-Laenge des Bildes
    //int localWorkSize = Math.min(device.getMaxWorkGroupSize(), 8); // Local work size dimensions
    //int localWorksize = worksize;
    //int globalWorksize = OpenCLUtil.roundUp(worksize, sizereconstruction*sizereconstruction); // rounded up to the nearest multiple of localworksize, because work domain must be multiple of worksize
   
    //Load and compile the cl-code and create the kernel function
    InputStream is = OpenCLGridAdd.class.getResourceAsStream("ParallelProject.cl");
    CLProgram program = null;
    try {
        program = context.createProgram(is).build();
    } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }
     
    // create the CLBuffer for the grids
    CLImageFormat format = new CLImageFormat(ChannelOrder.INTENSITY, ChannelType.FLOAT);
     
    CLKernel kernelFunction = program.createCLKernel ("projection");
     
    //CLBuffer<FloatBuffer> imageBuffer = context.createFloatBuffer(imageSize, Mem.READ_ONLY);
    //imageBuffer.getBuffer().rewind();
     
    // make sure OpenCL is tuned on / and things are on the device
    // grid in Buffer schreiben, Pixelweise
    OpenCLGrid2D sino = new OpenCLGrid2D(sinogram);
    sino.getDelegate().prepareForDeviceOperation();
    CLImage2d<FloatBuffer> sinoBuffer = context.createImage2d(sino.getDelegate().getCLBuffer().getBuffer(), sino.getSize()[0], sino.getSize()[1], format, Mem.READ_ONLY);
    
    // Create and set the texture
    //CLImage2d<FloatBuffer> imageGrid = context.createImage2d(imageBuffer.getBuffer(), grid.getSize()[0], grid.getSize()[1], format);
    //imageBuffer.release();
     
    CLCommandQueue commandQueue = device.createCommandQueue();
 
    // create output
    OpenCLGrid2D reconstruction = new OpenCLGrid2D(new Grid2D(sizereconstruction, sizereconstruction));
    reconstruction.getDelegate().prepareForDeviceOperation();
    //CLImage2d<FloatBuffer>  = context.createImage2d(reconstruction.getDelegate().getCLBuffer().getBuffer(), sizereconstruction, sizereconstruction, format, Mem.READ_ONLY);
     
    CLBuffer<FloatBuffer> recBuffer = reconstruction.getDelegate().getCLBuffer();
    // Write memory on the GPU
    commandQueue
    //worksize reinbringen?
    .putWriteImage(sinoBuffer, true)
    .putWriteBuffer(recBuffer, true)
    .finish();
 
    // Write kernel parameters
    //CLKernel kernel = program.createCLKernel("projectRayDriven2DCL");
    kernelFunction
        .putArg(sizereconstruction)
        .putArg(spacing)
        .putArg(deltaTheta)
        .putArg(deltaS)
        .putArg(maxS)
        .putArg(maxTheta)
        .putArg(sinoBuffer)
        .putArg(recBuffer);

//    // write back to grid2D
//    Grid2D result = new Grid2D(maxS, maxTheta);
//    result.setSpacing(deltaS, deltaTheta);
//    reconstructionBuffer.getBuffer().rewind();
//    for (int i = 0; i < result.getBuffer().length; ++i) {
//        result.getBuffer()[i] = reconstructionBuffer.getBuffer().get();
//    }
 
    // Check correct work group sizes
    int bpBlockSize[] = {worksize, worksize};
    int maxWorkGroupSize = device.getMaxWorkGroupSize();
    int[] realLocalSize = new int [] { Math.min((int)Math.pow(maxWorkGroupSize, 1/2.0), bpBlockSize[0]), Math.min((int)Math.pow(maxWorkGroupSize, 1/2.0), bpBlockSize[1])};
    //rounded up to the nrearest multiple of localWorkSize
    int[] globalWorkSize = new int[]{OpenCLUtil.roundUp(realLocalSize[0], sizereconstruction), OpenCLUtil.roundUp(realLocalSize[1], sizereconstruction)};
    
    //exectue kernel
    commandQueue.put2DRangeKernel(kernelFunction, 0, 0, globalWorkSize[0], globalWorkSize[1], realLocalSize[0], realLocalSize[1]).finish();
    reconstruction.getDelegate().notifyDeviceChange();
    
    // clean up
    commandQueue.release();
    kernelFunction.release();
    program.release();
    
    Grid2D result = new Grid2D(reconstruction);
    
    return result;
    }
    
    public static void main (String [] args){
    	ImageJ ij = new ImageJ();
    	//SheppLogan object = new SheppLogan(256);
    	// Size of the grid
    			int[] size = new int[] {128,128};
    			double [] spacing = {1,1};
    			float [] spacingConv = {1,1};
    			       
    	        
    			Phantom object = new Phantom(size[0],size[1],spacing);
    	object.show("The phantom");
        int ImDimX = 256;
        int ImDimY = ImDimX;
        float spacingX = 1.f;
        float spacingY = spacingX;
        float deltaS = 1.0f;
        float maxS = 256.0f; 
	    float maxTheta = 180;
	    float deltaTheta = 1.0f;
    	Grid2D sino = reconstructionCL(32, ImDimY, spacingX, deltaS, maxS, maxTheta, deltaTheta, object);
    	sino.show("The Reconstruction result");
    }
    
}