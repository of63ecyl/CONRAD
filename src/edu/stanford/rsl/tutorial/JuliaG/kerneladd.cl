__kernel void gridAddKernel(__global float* g1,__global float* g2, __global float* g3, int sizeX, int sizeY) {
	int iGID = get_global_id(0);
	if (iGID >= sizeX*sizeY){
		return;
	}
	g3[iGID]=g1[iGID] + g2[iGID];
}