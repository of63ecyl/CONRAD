typedef float TvoxelValue;
typedef float Tcoord_dev;

// Texture sampling
__constant sampler_t sampler = CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;

// Arguments: the first grid as texture,the result grid, the grid size
__kernel void backprojectorKernel(__read_only image2d_t sinoCL,__global TvoxelValue* gRes,__constant Tcoord_dev* gVolumeSize, __constant int maxThetaIndex)
{
	int gidx = get_group_id(0);
	int gidy = get_group_id(1);
	int lidx = get_local_id(0);
	int lidy = get_local_id(1);

	int locSizex = get_local_size(0);
	int locSizey = get_local_size(1);

	int x = get_global_id(0);
	int y = get_global_id(1);

	if (x >= gVolumeSize[0] || y >= gVolumeSize[1])
	{
		return;
	}


	// loop over the projection angles
	for (int i = 0; i < maxThetaIndex; i++) {
		// compute actual value for theta. Convert from deg to rad
		double theta = (deltaTheta * i * M_PI)/180;
		// pre-compute sine and cosines for faster computation
		double cosTheta = cos(theta);
		double sinTheta = sin(theta);
		// get detector direction vector (direction of ray shot)
		SimpleVector dirDetector = new SimpleVector(cosTheta,sinTheta); // 2d array?
		// compute world coordinate of current pixel
		double[] w = grid.indexToPhysical(x, y); // x * this.spacing[0] + this.origin[0], y * this.spacing[1] + this.origin[0]  --> mad24?
		// wrap into vector
		SimpleVector pixel = new SimpleVector(w[0], w[1]);  // 2d array?
		//  project pixel onto detector
		double s = SimpleOperators.multiplyInnerProd(pixel, dirDetector); // dot product: dot(x,y)
		// compute detector element index from world coordinates
		s += maxS/2; // [mm] // maxS uebergeben
		s /= deltaS; // [GU] deltaS uebergeben
		// get detector grid
		Grid1D subgrid = sino.getSubGrid(i); // how to get subgrid??
		// check detector bounds, continue if out of array
		if (subgrid.getSize()[0] <= s + 1
				||  s < 0)
			continue;
		// get interpolated value
		float val = InterpolationOperators.interpolateLinear(subgrid, s);  // how to do Interpolation??
		// sum value to sinogram
		grid.addAtIndex(x, y, val); // add interpolated value to prior value
	}


	return;
}
