typedef float TvoxelValue;
typedef float Tcoord_dev;

// Texture sampling
__constant sampler_t sampler = CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;

// Arguments: the first grid as texture,the result grid, the grid size
__kernel void backprojectorKernel(__read_only image2d_t sinoCL,
				  __global TvoxelValue* gRes,
				  int sizereconstruction,
				  //__constant Tcoord_dev* gVolumeSize, 
				  int maxThetaIndex,
				  float maxS,
				  float deltaS,
				  float deltaTheta,
				  float spacingX,
				  float spacingY)
 				
{
	//int gidx = get_group_id(0);
	//int gidy = get_group_id(1);
	//int lidx = get_local_id(0);
	//int lidy = get_local_id(1);

	//int locSizex = get_local_size(0);
	//int locSizey = get_local_size(1);

	const unsigned int x = get_global_id(0); // theta
	const unsigned int y = get_global_id(1); // s

	if (x > sizereconstruction || y > sizereconstruction)
	{
		return;
	}

	unsigned int yStride = sizereconstruction;
	unsigned long idx = y*yStride + x;
	
	// x * this.spacing[0] + this.origin[0], y * this.spacing[1] + this.origin[0]
	float worldX = x*spacingX-((sizereconstruction -1) * (spacingX/2));
	float worldY = y*spacingY-((sizereconstruction -1) * (spacingY/2));
	
	float detectorLength =(maxS-1)*deltaS;
	float sum = 0.0f;

	// loop over the projection angles
	for (int i = 0; i < maxThetaIndex; i++) {
		// compute actual value for theta. Convert from deg to rad
		float theta = (deltaTheta * i * M_PI)/180;
		// pre-compute sine and cosines for faster computation
		float cosTheta = cos(theta);
		float sinTheta = sin(theta);
		// get detector direction vector (direction of ray shot)
		float2 dirDetector = {cosTheta,sinTheta}; // 2d array?
		// compute world coordinate of current pixel
		float2 w = {worldX,worldY};
		//  project pixel onto detector
		float s = dot(w, dirDetector); // dot product: dot(x,y)
		// compute detector element index from world coordinates
		s = s + ((detectorLength)/2); // [mm] //
		s = s / deltaS; // [GU] deltaS uebergeben
		
		// get location in sinogram
		float2 sinoLoc = {s+0.5f,i+0.5f};
		
		// get interpolated value
		float val = read_imagef(sinoCL,sampler,sinoLoc).x;
		
		sum = sum+val;
		
	}
	
	gRes[idx] = sum;
	return;
}
