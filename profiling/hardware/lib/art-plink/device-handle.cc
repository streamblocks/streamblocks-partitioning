#include "device-handle.h"

void CL_CALLBACK completion_handler(cl_event event, cl_int cmd_status,
                                    void *info) {

  cl_command_type command;
  OCL_CHECK(clGetEventInfo(event, CL_EVENT_COMMAND_TYPE,
                           sizeof(cl_command_type), &command, NULL));
  const char *command_str;
  EventInfo *event_info = (EventInfo *)info;

  switch (command) {
  case CL_COMMAND_NDRANGE_KERNEL:
    command_str = "kernel";
    break;
  case CL_COMMAND_MIGRATE_MEM_OBJECTS:
    command_str = "migrate";
    break;
  case CL_COMMAND_WRITE_BUFFER:
    command_str = "write";
    break;
  case CL_COMMAND_READ_BUFFER:
    command_str = "read";
    break;
  default:
    command_str = "unsupported";
  }
  char callback_msg[2048];

  if (OCL_VERBOSE)
    sprintf(callback_msg, "<<Completed %s (%d)>>: %s\n", command_str,
            event_info->counter, (char *)event_info->msg);
  event_info->counter++;

  OCL_MSG("%s", callback_msg);
  fflush(stdout);
}
void on_completion(cl_event event, void *info) {

  OCL_CHECK(
      clSetEventCallback(event, CL_COMPLETE, completion_handler, (void *)info));
}

void DeviceHandleConstructor(DeviceHandle_t *dev, int num_inputs,
                             int num_outputs, char *kernel_name,
                             char *target_device_name, char *dir, bool hw_emu) {
  // cl_int err;
  // dev->buffer_size = 0;
  dev->num_inputs = num_inputs;
  dev->num_outputs = num_outputs;
  dev->mem_alignment = MEM_ALIGNMENT;

  OCL_MSG("Initializing device\n");
  char cl_platform_vendor[1001];
  // Get all platforms and then select Xilinx platform
  cl_platform_id platforms[16]; // platform id
  cl_uint platform_count;
  cl_uint platform_found = 0;
  cl_int err;
  err = clGetPlatformIDs(16, platforms, &platform_count);
  if (err != CL_SUCCESS) {
    OCL_ERR("Failed to find an OpenCL platform!\n");
    exit(EXIT_FAILURE);
  }
  OCL_MSG("Found %d platforms\n", platform_count);
  // Find Xilinx Plaftorm
  for (cl_uint iplat = 0; iplat < platform_count; iplat++) {
    err = clGetPlatformInfo(platforms[iplat], CL_PLATFORM_VENDOR, 1000,
                            (void *)cl_platform_vendor, NULL);
    if (err != CL_SUCCESS) {
      OCL_ERR("clGetPlatformInfo(CL_PLATFORM_VENDOR) failed!\n");

      exit(EXIT_FAILURE);
    }
    if (strcmp(cl_platform_vendor, "Xilinx") == 0) {
      OCL_MSG("Selected platform %d from %s\n", iplat, cl_platform_vendor);
      dev->world.platform_id = platforms[iplat];
      platform_found = 1;
    }
  }
  if (!platform_found) {
    OCL_MSG("Platform Xilinx not found. Exit.\n");
    exit(EXIT_FAILURE);
  }
  cl_uint num_devices;
  cl_uint device_found = 0;
  cl_device_id devices[16]; // compute device id
  char cl_device_name[1001];
  err = clGetDeviceIDs(dev->world.platform_id, CL_DEVICE_TYPE_ACCELERATOR, 16,
                       devices, &num_devices);
  OCL_MSG("Found %d devices\n", num_devices);
  if (err != CL_SUCCESS) {
    OCL_ERR("Failed to create a device group!\n");
    exit(EXIT_FAILURE);
  }
  // iterate all devices to select the target device.
  for (cl_uint i = 0; i < num_devices; i++) {
    err = clGetDeviceInfo(devices[i], CL_DEVICE_NAME, 1024, cl_device_name, 0);
    if (err != CL_SUCCESS) {
      OCL_ERR("Failed to get device name for device %d!\n", i);
      exit(EXIT_FAILURE);
    }
    OCL_MSG("CL_DEVICE_NAME %s\n", cl_device_name);
    if (strcmp(cl_device_name, target_device_name) == 0) {
      dev->world.device_id = devices[i];
      device_found = 1;
      OCL_MSG("Selected %s as the target device\n", cl_device_name);
    }
  }

  if (!device_found) {
    OCL_ERR("Target device %s not found. Exit.\n", target_device_name);
    exit(EXIT_FAILURE);
  }

  // Create a compute context
  //
  dev->world.context =
      clCreateContext(0, 1, &dev->world.device_id, NULL, NULL, &err);
  if (!dev->world.context) {
    OCL_ERR("Error: Failed to create a compute context!\n");
    exit(EXIT_FAILURE);
  }

  // Create a command commands
  dev->world.command_queue =
      clCreateCommandQueue(dev->world.context, dev->world.device_id,
                           CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE | 
													 CL_QUEUE_PROFILING_ENABLE, &err);
  if (!dev->world.command_queue) {
    OCL_ERR("Failed to create a command commands!\n");
    OCL_ERR("code %i\n", err);
    exit(EXIT_FAILURE);
  }

  cl_int status;

  // Create Program Objects
  // Load binary from disk
  unsigned char *kernelbinary;
  char xclbin[1024];
  // "./xclbin/SW_kernel.hw.xilinx_kcu1500_dynamic_5_0.xclbin";
  if (hw_emu)
    sprintf(xclbin, "%s/%s.hw_emu.%s.xclbin", dir, kernel_name,
            target_device_name);
  else
    sprintf(xclbin, "%s/%s.hw.%s.xclbin", dir, kernel_name, target_device_name);
  // xclbin
  //------------------------------------------------------------------------------
  OCL_MSG("INFO: loading xclbin %s\n", xclbin);
  cl_int n_i0 = load_file_to_memory(xclbin, (char **)&kernelbinary);

  if (n_i0 < 0) {
    OCL_ERR("failed to load kernel from xclbin: %s\n", xclbin);

    exit(EXIT_FAILURE);
  }
  size_t n0 = n_i0;

  // Create the compute program
  dev->program = clCreateProgramWithBinary(
      dev->world.context, 1, &dev->world.device_id, &n0,
      (const unsigned char **)&kernelbinary, &status, &err);
  free(kernelbinary);

  if ((!dev->program) || (err != CL_SUCCESS)) {
    OCL_ERR("Failed to create compute program from binary %d!\n", err);

    exit(EXIT_FAILURE);
  }

  // Build the program execute
  err = clBuildProgram(dev->program, 0, NULL, NULL, NULL, NULL);

  if (err != CL_SUCCESS) {
    size_t len;
    char buffer[2048];
    OCL_ERR("Failed to build program executable!\n");
    clGetProgramBuildInfo(dev->program, dev->world.device_id,
                          CL_PROGRAM_BUILD_LOG, sizeof(buffer), buffer, &len);
    OCL_ERR("%s\n", buffer);
    OCL_ERR("Test failed\n");
    exit(EXIT_FAILURE);
  }

  dev->kernel = clCreateKernel(dev->program, kernel_name, &err);

  if (!dev->kernel || err != CL_SUCCESS) {
    OCL_ERR("Failed to create compute kernel!\n");
    exit(EXIT_SUCCESS);
  }

  dev->global = 1;
  dev->local = 1;
  DeviceHandleInitEvents(dev);
}
cl_int load_file_to_memory(const char *filename, char **result) {
  cl_int size = 0;
  FILE *f = fopen(filename, "rb");
  if (f == NULL) {
    *result = NULL;
    return -1; // -1 means file opening fail
  }
  fseek(f, 0, SEEK_END);
  size = ftell(f);
  fseek(f, 0, SEEK_SET);
  *result = (char *)malloc(size + 1);
  if (size != fread(*result, sizeof(char), size, f)) {
    free(*result);
    return -2; // -2 means file reading fail
  }
  fclose(f);
  (*result)[size] = 0;
  return size;
}

void DeviceHandleRun(DeviceHandle_t *dev) {

  OCL_MSG("Migrating to Device\n");
  DeviceHandleEnqueueWriteBuffers(dev);

  OCL_MSG("Setting kernel arguments\n");
  DeviceHandleSetArgs(dev);

  OCL_MSG("Starting execution\n");
  DeviceHandleEnqueueExecution(dev);

  OCL_MSG("Migrating to host\n");
  DeviceHandleEnqueueReadBuffers(dev);
}

void DeviceHandleTerminate(DeviceHandle_t *dev) {
  //  clFlush(dev->world.command_queue);
  //  clFinish(dev->world.command_queue);
  OCL_CHECK(clReleaseKernel(dev->kernel));
  OCL_CHECK(clReleaseProgram(dev->program));
  DeviceHandleReleaseMemObjects(dev);
  OCL_CHECK(clReleaseCommandQueue(dev->world.command_queue));
  OCL_CHECK(clReleaseContext(dev->world.context));
  DeviceHandleFreeEvents(dev);
}

void DeviceHandleReleaseKernelEvent(DeviceHandle_t *dev) {
  OCL_MSG("Releasing kernel event\n");
  OCL_CHECK(clReleaseEvent(dev->kernel_event));
}

void DeviceHandleSetKernelCommand(DeviceHandle_t *dev, uint64_t cmd) {
  dev->kernel_command = cmd;
  dev->command_is_set = 1;
}
void DeviceHandleCreateCLBuffers(DeviceHandle_t*	dev, size_t*	cl_write_buffer_size, size_t*	cl_read_buffer_size) {
	cl_int err;
	OCL_MSG("Creating input CL buffers\n");

	// -- input buffers
	dev->source_Out_pass_In_cl_buffer_alloc_size = cl_write_buffer_size[0];
	dev->source_Out_pass_In_cl_buffer = clCreateBuffer(dev->world.context, CL_MEM_READ_ONLY, cl_write_buffer_size[0] * sizeof(uint32_t), NULL, &err);
	if (err != CL_SUCCESS)
		OCL_ERR("clCreateBuffer failed for source_Out_pass_In_cl_buffer with error code %d.", err)

	// -- output buffer
	dev->pass_Out_sink_In_cl_buffer_alloc_size = cl_read_buffer_size[0];
	dev->pass_Out_sink_In_cl_buffer = clCreateBuffer(dev->world.context, CL_MEM_WRITE_ONLY, cl_read_buffer_size[0] * sizeof(uint32_t), NULL, &err);
	if (err != CL_SUCCESS)
		OCL_ERR("clCreateBuffer failed for pass_Out_sink_In_cl_buffer with error code %d.", err)

	// -- consumed and produced size of streams
	dev->source_Out_pass_In_cl_size = clCreateBuffer(dev->world.context, CL_MEM_WRITE_ONLY, sizeof(uint32_t), NULL, &err);
	if (err != CL_SUCCESS)
		OCL_ERR("clCreateBuffer failed for source_Out_pass_In_cl_size with error code %d.", err)
	dev->pass_Out_sink_In_cl_size = clCreateBuffer(dev->world.context, CL_MEM_WRITE_ONLY, sizeof(uint32_t), NULL, &err);
	if (err != CL_SUCCESS)
		OCL_ERR("clCreateBuffer failed for pass_Out_sink_In_cl_size with error code %d.", err)

	// -- external memories used hardware actors
}
void DeviceHandleInitEvents(DeviceHandle_t*	dev) {
	dev->write_buffer_event = (cl_event*) calloc (dev->num_inputs, sizeof(cl_event));
	dev->write_buffer_event_info = (EventInfo*) calloc (dev->num_inputs, sizeof(EventInfo));

	for(int i = 0; i < dev->num_inputs; i++) {
		dev->write_buffer_event_info[i].counter = 0;
		dev->write_buffer_event_info[i].active = false;
		sprintf(dev->write_buffer_event_info[i].msg, "write buffer event %d", i);
	}
	dev->read_buffer_event = (cl_event*) calloc (dev->num_outputs, sizeof(cl_event));
	dev->read_buffer_event_info = (EventInfo*) calloc (dev->num_outputs, sizeof(EventInfo));
	dev->read_buffer_event_wait_list = (cl_event*) calloc (dev->num_outputs, sizeof(cl_event));

	for(int i = 0; i < dev->num_outputs; i++) {
		dev->read_buffer_event_info[i].counter = 0;
		dev->read_buffer_event_info[i].active = false;
		sprintf(dev->read_buffer_event_info[i].msg, "read buffer event %d", i);
	}

	dev->read_size_event = (cl_event*) calloc ((dev->num_inputs + dev->num_outputs), sizeof(cl_event));
	dev->read_size_event_info = (EventInfo*) calloc ((dev->num_inputs + dev->num_outputs), sizeof(EventInfo));
	for (int i = 0; i < dev->num_inputs + dev->num_outputs; i++) {
		dev->read_size_event_info[i].counter = 0;
		dev->read_size_event_info[i].active = false;
		sprintf(dev->read_size_event_info[i].msg, "read size event %d", i);
	}

	dev->kernel_event_wait_list = (cl_event*) calloc (dev->num_inputs, sizeof(cl_event));
	dev->kernel_event_info.counter = 0;
	dev->kernel_event_info.active = false;
	sprintf(dev->kernel_event_info.msg, "kernel event");

}
void DeviceHandleSetArgs(DeviceHandle_t*	dev) {
	OCL_MSG("Setting kernel args\n");
	// -- request size for source_Out_pass_In
	OCL_CHECK(
		clSetKernelArg(dev->kernel, 0, sizeof(cl_uint), &dev->source_Out_pass_In_request_size)
	);
	// -- device available size for outputs, should be at most as big as buffer_size
	OCL_CHECK(
		clSetKernelArg(dev->kernel, 1, sizeof(cl_uint), &dev->pass_Out_sink_In_cl_buffer_alloc_size)
	);
	// -- stream size buffer for source_Out_pass_In
	OCL_CHECK(
		clSetKernelArg(dev->kernel, 2, sizeof(cl_mem), &dev->source_Out_pass_In_cl_size)
	);
	// -- device buffer object for source_Out_pass_In
	OCL_CHECK(
		clSetKernelArg(dev->kernel, 3, sizeof(cl_mem), &dev->source_Out_pass_In_cl_buffer)
	);
	// -- stream size buffer for pass_Out_sink_In
	OCL_CHECK(
		clSetKernelArg(dev->kernel, 4, sizeof(cl_mem), &dev->pass_Out_sink_In_cl_size)
	);
	// -- device buffer object for pass_Out_sink_In
	OCL_CHECK(
		clSetKernelArg(dev->kernel, 5, sizeof(cl_mem), &dev->pass_Out_sink_In_cl_buffer)
	);
	// -- kernel command arg
	OCL_CHECK(
		clSetKernelArg(dev->kernel, 6, sizeof(cl_ulong), &dev->kernel_command)
	);

	// -- external memories

}
void DeviceHandleEnqueueWriteBuffers(DeviceHandle_t*	dev) {
	size_t req_sz = 1;

	if (dev->write_buffer_event_info[0].active == true) 
		OCL_ERR("Detected unreleased write buffer event for source_Out_pass_In\n");
	if (dev->source_Out_pass_In_request_size > 0) { // -- make sure there is something to send
		// -- DMA write transfer for source_Out_pass_In\n
		OCL_MSG("Enqueueing write for source_Out_pass_In\n");
		OCL_CHECK(
			clEnqueueWriteBuffer(
				dev->world.command_queue, // -- the command queue
				dev->source_Out_pass_In_cl_buffer, // -- the cl device buffer
				CL_FALSE, // -- blocking write operation
				0, // -- buffer offset, not use
				dev->source_Out_pass_In_request_size * sizeof(uint32_t), // -- size of data transfer in byte
				dev->source_Out_pass_In_buffer, // -- pointer to the host memory
				0, // -- number of events in wait list, writes do not wait for anything
				NULL, // -- the event wait list, not used for writes
				&dev->write_buffer_event[0])); // -- the generated event

				// -- register call back for the write event
				dev->write_buffer_event_info[0].active = true;
				on_completion(dev->write_buffer_event[0], &dev->write_buffer_event_info[0]);
	}

}
void DeviceHandleEnqueueExecution(DeviceHandle_t*	dev) {
	OCL_MSG("Equeueing kernel\n");

	uint32_t num_events_to_wait_on = 0;
	for (int i = 0; i < dev->num_inputs; i ++) {
		if (dev->write_buffer_event_info[i].active == true)
			dev->kernel_event_wait_list[num_events_to_wait_on++] = dev->write_buffer_event[i];
	}

	cl_event* event_list = num_events_to_wait_on > 0 ? dev->kernel_event_wait_list : NULL;
	OCL_CHECK(
		clEnqueueNDRangeKernel(
			dev->world.command_queue, // -- command queue
			dev->kernel, // -- kernel
			1, // -- work dimension
			NULL, // -- global work offset
			&dev->global, //-- global work size
			&dev->local, //-- local work size
			num_events_to_wait_on, // -- number of events to wait on
			event_list, // -- event wait list
			&dev->kernel_event)); // -- the generated event
	on_completion(dev->kernel_event, &dev->kernel_event_info);
}
void DeviceHandleEnqueueReadSize(DeviceHandle_t*	dev) {

	// -- Enqueue read for i/o size buffers
	OCL_MSG("Enqueue read size for source_Out_pass_In\n");
	OCL_CHECK(
		clEnqueueReadBuffer(
			dev->world.command_queue, // -- command queue
			dev->source_Out_pass_In_cl_size, // -- device buffer
			CL_FALSE, //-- blocking read
			0, // -- offset
			sizeof(uint32_t), // -- size of the read transfer in bytes
			dev->source_Out_pass_In_size, // -- host buffer for the stream size
			1, // -- number of events to wait on
			&dev->kernel_event, // -- the list of events to wait on
			&dev->read_size_event[0])); // -- the generated event

			// -- register event call back 
			on_completion(dev->read_size_event[0], &dev->read_size_event_info[0]);

	OCL_MSG("Enqueue read size for pass_Out_sink_In\n");
	OCL_CHECK(
		clEnqueueReadBuffer(
			dev->world.command_queue, // -- command queue
			dev->pass_Out_sink_In_cl_size, // -- device buffer
			CL_FALSE, //-- blocking read
			0, // -- offset
			sizeof(uint32_t), // -- size of the read transfer in bytes
			dev->pass_Out_sink_In_size, // -- host buffer for the stream size
			1, // -- number of events to wait on
			&dev->kernel_event, // -- the list of events to wait on
			&dev->read_size_event[1])); // -- the generated event

			// -- register event call back 
			on_completion(dev->read_size_event[1], &dev->read_size_event_info[1]);


}
void DeviceHandleEnqueueReadBuffers(DeviceHandle_t*	dev) {
	// -- Enqueue read for output buffers
	if (dev->read_buffer_event_info[0].active == true) 
		OCL_ERR("Detected unreleased read buffer event for pass_Out_sink_In\n");
	if (dev->pass_Out_sink_In_size[0] > 0) {// -- only read if something was produced
		OCL_CHECK(
			clEnqueueReadBuffer(
				dev->world.command_queue, // -- command queue
				dev->pass_Out_sink_In_cl_buffer, // -- device buffer
				CL_FALSE, //-- blocking read
				0, // -- offset
				dev->pass_Out_sink_In_size[0] * sizeof(uint32_t), // -- size of the read transfer in bytes
				dev->pass_Out_sink_In_buffer, // -- host buffer for the stream size
				1, // -- number of events to wait on
				&dev->kernel_event, // -- the list of events to wait on
				&dev->read_buffer_event[0])); // -- the generated event

				// -- register event call back 
				dev->read_buffer_event_info[0].active = true;
				on_completion(dev->read_buffer_event[0], &dev->read_buffer_event_info[0]);

	}
}
void DeviceHandleReleaseMemObjects(DeviceHandle_t*	dev) {

	OCL_MSG("Releasing mem object source_Out_pass_In_cl_buffer\n");
	OCL_CHECK(
		clReleaseMemObject(dev->source_Out_pass_In_cl_buffer)
	);
	OCL_MSG("Releasing mem object source_Out_pass_In_cl_size\n");
	OCL_CHECK(
		clReleaseMemObject(dev->source_Out_pass_In_cl_size)
	);


	OCL_MSG("Releasing mem object pass_Out_sink_In_cl_buffer\n");
	OCL_CHECK(
		clReleaseMemObject(dev->pass_Out_sink_In_cl_buffer)
	);
	OCL_MSG("Releasing mem object pass_Out_sink_In_cl_size\n");
	OCL_CHECK(
		clReleaseMemObject(dev->pass_Out_sink_In_cl_size)
	);

}
// -- get pointer methods

// -- get pointers for source_Out_pass_In
uint32_t* DeviceHandleGet_source_Out_pass_In_buffer_ptr(DeviceHandle_t*	dev) { return dev->source_Out_pass_In_buffer; }
uint32_t* DeviceHandleGet_source_Out_pass_In_size_ptr(DeviceHandle_t*	dev) { return dev->source_Out_pass_In_size; }


// -- get pointers for pass_Out_sink_In
uint32_t* DeviceHandleGet_pass_Out_sink_In_buffer_ptr(DeviceHandle_t*	dev) { return dev->pass_Out_sink_In_buffer; }
uint32_t* DeviceHandleGet_pass_Out_sink_In_size_ptr(DeviceHandle_t*	dev) { return dev->pass_Out_sink_In_size; }


 // -- set pointer methods

// -- set pointers for source_Out_pass_In
void DeviceHandleSet_source_Out_pass_In_buffer_ptr(DeviceHandle_t*	dev, uint32_t*	ptr) { dev->source_Out_pass_In_buffer = ptr; }

void DeviceHandleSet_source_Out_pass_In_size_ptr(DeviceHandle_t*	dev, uint32_t*	ptr) { dev->source_Out_pass_In_size = ptr; }


// -- set pointers for pass_Out_sink_In
void DeviceHandleSet_pass_Out_sink_In_buffer_ptr(DeviceHandle_t*	dev, uint32_t*	ptr) { dev->pass_Out_sink_In_buffer = ptr; }

void DeviceHandleSet_pass_Out_sink_In_size_ptr(DeviceHandle_t*	dev, uint32_t*	ptr) { dev->pass_Out_sink_In_size = ptr; }


void DeviceHandleReleaseWriteEvents(DeviceHandle_t*	dev) {
	OCL_MSG("Releasing write buffer events..\n");
	for (int i = 0; i < dev->num_inputs; i++) {
		if (dev->write_buffer_event_info[i].active == true)
			OCL_CHECK(
				clReleaseEvent(dev->write_buffer_event[i])
			);
			dev->write_buffer_event_info[i].active = false;
	}
	OCL_MSG("All write buffer events released.\n");
}
void DeviceHandleReleaseReadSizeEvents(DeviceHandle_t*	dev) {
	for (int i = 0; i < dev->num_inputs + dev->num_outputs; i++) {
		OCL_CHECK(
			clReleaseEvent(dev->read_size_event[i]);
		);
	}
}
void DeviceHandleReleaseReadBufferEvents(DeviceHandle_t*	dev) {
	OCL_MSG("Releasing read events\n");
	for (int i = 0; i < dev->num_outputs; i++) {
		if (dev->read_buffer_event_info[i].active == true)
			OCL_CHECK(
				clReleaseEvent(dev->read_buffer_event[i])
			);
			dev->read_buffer_event_info[i].active = false;
	}
}
void DeviceHandleWaitForReadSize(DeviceHandle_t*	dev) {
	OCL_MSG("Waiting on read size events\n");
	OCL_CHECK(
		clWaitForEvents(dev->num_inputs  + dev->num_outputs, dev->read_size_event)
	);

}

void DeviceHandleWaitForReadBuffers(DeviceHandle_t*	dev) {

	uint32_t num_events_to_wait_on = 0;
	for (int i = 0; i < dev->num_outputs; i ++) {
		if (dev->read_buffer_event_info[i].active == true)
			dev->read_buffer_event_wait_list[num_events_to_wait_on++] = dev->read_buffer_event[i];
	}

	cl_event* event_list = num_events_to_wait_on > 0 ? dev->read_buffer_event_wait_list : NULL;
	OCL_MSG("Waiting on output buffers\n");
	if (num_events_to_wait_on > 0)
		OCL_CHECK(
			clWaitForEvents(num_events_to_wait_on, dev->read_buffer_event_wait_list)
		);

}
void DeviceHandleFreeEvents(DeviceHandle_t*	dev) {
	OCL_MSG("Freeing write buffer events...\n");
	free (dev->write_buffer_event);
	free (dev->write_buffer_event_info);

	OCL_MSG("Freeing read buffer events...\n");
	free (dev->read_buffer_event);
	free (dev->read_buffer_event_info);
	free (dev->read_buffer_event_wait_list);

	OCL_MSG("Freeing read size events... \n");
	free (dev->read_size_event);
	free (dev->read_size_event_info);

	free (dev->kernel_event_wait_list);

}
// -- set request size

// -- set request size for source_Out_pass_In
void DeviceHandleSet_source_Out_pass_In_request_size(DeviceHandle_t*	dev, uint32_t	req_sz) { dev->source_Out_pass_In_request_size = req_sz; }

