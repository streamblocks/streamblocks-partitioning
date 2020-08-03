#ifndef DEVICE_HANDLE_H
#define DEVICE_HANDLE_H
#include <CL/cl_ext.h>
#include <CL/opencl.h>
#include <assert.h>
#include <fcntl.h>
#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>


// -- OpenCL and actor specific defines
// The definition below is can be set by CMAKE option VERBOSE_OPENCL
// #define OCL_VERBOSE			1 
#define OCL_ERROR			1
#define MEM_ALIGNMENT			4096

#define NUM_INPUTS			1
#define NUM_OUTPUTS			1

// -- helper macros

#define OCL_CHECK(call)		\
	do {		\
		cl_int err = call;		\
		if (err != CL_SUCCESS) { 		\
			fprintf(stderr, "Error calling" #call ", error code is: %d", err);		\
			exit(EXIT_FAILURE);		\
			}		\
		} while (0);

#define OCL_MSG(fmt, args...)		\
	do {		\
		if (OCL_VERBOSE)		\
			fprintf(stdout, "OCL_MSG:%s():%d: " fmt, __func__, __LINE__, ##args);\
		} while (0);

#define OCL_ERR(fmt, args...)		\
	do {		\
		if (OCL_ERROR)		\
			fprintf(stderr, "OCL_ERR:%s():%d: " fmt, __func__, __LINE__, ##args);\
		} while (0);

// -- event handling info
typedef struct EventInfo {
	size_t counter;
	bool active;
	char msg[128];
} EventInfo;

typedef struct OCLWorld {
	cl_context context;
	cl_platform_id platform_id;
	cl_device_id device_id;
	cl_command_queue command_queue;
} OCLWorld;

// -- Device Handle struct
typedef struct DeviceHandle_t {

	OCLWorld world;


	cl_program program;


	cl_kernel kernel;


	size_t global;


	size_t local;


	uint32_t num_inputs;


	uint32_t num_outputs;


	size_t mem_alignment;


	// -- the kernel command word (deprecated)
	uint64_t kernel_command;


	// -- the kernel command status (deprecated)
	uint32_t command_is_set;


	// -- an array containing write buffer events
	cl_event* write_buffer_event;


	// -- write buffer event info
	EventInfo* write_buffer_event_info;


	// -- an array containing read size events
	cl_event* read_size_event;


	// -- read size event info
	EventInfo* read_size_event_info;


	// -- an array containing read buffer events
	cl_event* read_buffer_event;


	// -- read buffer event info
	EventInfo* read_buffer_event_info;


	// -- events for the read buffers to wait on
	cl_event* read_buffer_event_wait_list;


	// -- kernel enqueue event
	cl_event kernel_event;


	// -- kernel enqueue event info
	EventInfo kernel_event_info;


	// -- list of events the kernel has to wait on
	cl_event* kernel_event_wait_list;


	// -- Size of transfer for source_Out_pass_In
	uint32_t source_Out_pass_In_request_size;


	// -- host buffer for port source_Out_pass_In
	uint32_t* source_Out_pass_In_buffer;


	// -- host size buffer for port source_Out_pass_In
	uint32_t* source_Out_pass_In_size;


	// -- device buffer for port source_Out_pass_In
	cl_mem source_Out_pass_In_cl_buffer;


	// -- allocated size for the cl buffer of port source_Out_pass_In
	size_t source_Out_pass_In_cl_buffer_alloc_size;


	// -- device size buffer for port source_Out_pass_In
	cl_mem source_Out_pass_In_cl_size;


	// -- host buffer for port pass_Out_sink_In
	uint32_t* pass_Out_sink_In_buffer;


	// -- host size buffer for port pass_Out_sink_In
	uint32_t* pass_Out_sink_In_size;


	// -- device buffer for port pass_Out_sink_In
	cl_mem pass_Out_sink_In_cl_buffer;


	// -- allocated size for the cl buffer of port pass_Out_sink_In
	size_t pass_Out_sink_In_cl_buffer_alloc_size;


	// -- device size buffer for port pass_Out_sink_In
	cl_mem pass_Out_sink_In_cl_size;

} DeviceHandle_t;

// -- method declarations

void DeviceHandleConstructor(DeviceHandle_t*	dev, int	num_inputs, int	num_outputs, char*	kernel_name, char*	target_device_name, char*	dir, bool	hw_emu);


void DeviceHandleTerminate(DeviceHandle_t*	dev);


cl_int load_file_to_memory(const char*	filename, char**	result);


void DeviceHandleCreateCLBuffers(DeviceHandle_t*	dev, size_t*	cl_write_buffer_size, size_t*	cl_read_buffer_size);


void DeviceHandleSetArgs(DeviceHandle_t*	dev);


void DeviceHandleEnqueueExecution(DeviceHandle_t*	dev);


void DeviceHandleEnqueueWriteBuffers(DeviceHandle_t*	dev);


void DeviceHandleEnqueueReadSize(DeviceHandle_t*	dev);


void DeviceHandleEnqueueReadBuffers(DeviceHandle_t*	dev);


void DeviceHandleWaitForReadSize(DeviceHandle_t*	dev);


void DeviceHandleWaitForReadBuffers(DeviceHandle_t*	dev);


void DeviceHandleRun(DeviceHandle_t*	dev);


void DeviceHandleInitEvents(DeviceHandle_t*	dev);


void DeviceHandleReleaseMemObjects(DeviceHandle_t*	dev);


void DeviceHandleReleaseReadSizeEvents(DeviceHandle_t*	dev);


void DeviceHandleReleaseReadBufferEvents(DeviceHandle_t*	dev);


void DeviceHandleReleaseKernelEvent(DeviceHandle_t*	dev);


void DeviceHandleReleaseWriteEvents(DeviceHandle_t*	dev);


void DeviceHandleFreeEvents(DeviceHandle_t*	dev);


void DeviceHandleSet_source_Out_pass_In_request_size(DeviceHandle_t*	dev, uint32_t	req_sz);


void DeviceHandleSet_source_Out_pass_In_buffer_ptr(DeviceHandle_t*	dev, uint32_t*	ptr);


void DeviceHandleSet_source_Out_pass_In_size_ptr(DeviceHandle_t*	dev, uint32_t*	ptr);


void DeviceHandleSet_pass_Out_sink_In_buffer_ptr(DeviceHandle_t*	dev, uint32_t*	ptr);


void DeviceHandleSet_pass_Out_sink_In_size_ptr(DeviceHandle_t*	dev, uint32_t*	ptr);


uint32_t* DeviceHandleGet_source_Out_pass_In_buffer_ptr(DeviceHandle_t*	dev);


uint32_t* DeviceHandleGet_source_Out_pass_In_size_ptr(DeviceHandle_t*	dev);


uint32_t* DeviceHandleGet_pass_Out_sink_In_buffer_ptr(DeviceHandle_t*	dev);


uint32_t* DeviceHandleGet_pass_Out_sink_In_size_ptr(DeviceHandle_t*	dev);

#endif // DEVICE_HANDLE_H
