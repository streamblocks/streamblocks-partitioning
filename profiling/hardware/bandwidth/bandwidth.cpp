#include "device-port.h"
#include <boost/program_options.hpp>
#include <fstream>
#include <memory>
#include <random>
#include <sstream>

// -- utility functions
int randint(int min, int max) {

  static std::random_device rd;
  static std::mt19937 gen(rd());
  std::uniform_int_distribution<> distrib(min, max);
  return distrib(gen);
}

template <typename T> struct LoopbackTester {

  cl::Context context;
  cl::Platform platform;
  cl::Device device;
  cl::CommandQueue command_queue;
  cl::Program program;
  cl::Kernel kernel;

  std::vector<cl::Event> kernel_event;
  ocl_device::EventInfo kernel_event_info;

  std::vector<cl::Event> kernel_wait_events;

  std::size_t call_index;

  std::vector<ocl_device::DevicePort> input_ports;
  std::vector<ocl_device::DevicePort> output_ports;

  std::size_t payload_size;

  std::vector<ocl_device::EventProfile> kernel_stats;

  LoopbackTester(const int width, const std::size_t payload_size,
                 const std::string kernel_name, const std::string dir)
      : payload_size(payload_size) {

    cl_int error;
    OCL_MSG("Initializing the device\n");

    // get all devices
    std::vector<cl::Device> devices = xcl::get_xil_devices();
    cl::Device device = devices[0];

    // Creating Context and Command Queue for selected Device
    context = cl::Context(device);
    OCL_CHECK(error, command_queue = cl::CommandQueue(
                         context, device,
                         CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE, &error));
    std::string device_name = device.getInfo<CL_DEVICE_NAME>();

    std::string xclbin_name;
    {
      std::stringstream builder;

      builder << dir << "/" << kernel_name;
      auto emu_mode = std::getenv("XCL_EMULATION_MODE");
      if (emu_mode == NULL)
        builder << ".hw";
      else {
        if (strcmp(emu_mode, "hw_emu") == 0)
          builder << ".hw_emu";
        else
          OCL_ERR("Unsupported emulation mode %s\n", emu_mode);
      }

      builder << ".xclbin";

      xclbin_name = builder.str();
    }

    auto bins = xcl::import_binary_file(xclbin_name);
    devices.resize(1);
    OCL_CHECK(error,
              program = cl::Program(context, devices, bins, NULL, &error));

    // -- creat the kernel
    kernel = cl::Kernel(program, kernel_name.c_str());

    // -- init kernel event
    kernel_event_info.init(std::string("kernel event"));
    kernel_event.emplace_back();

    OCL_MSG("LoopbackTester::Building input and output ports\n");

    for (int ix = 0; ix < width; ix++) {
      std::stringstream input_name_string;
      input_name_string << "input_" << ix;
      std::string input_name = input_name_string.str();
      input_ports.emplace_back(
          ocl_device::PortAddress(input_name), // name of the port
          ocl_device::PortType(ocl_device::IOType::INPUT,
                               sizeof(T)), // port type
          true                             // enable stat collection
      );
      std::stringstream output_name_string;
      output_name_string << "output_" << ix;
      std::string output_name = output_name_string.str();
      output_ports.emplace_back(
          ocl_device::PortAddress(output_name), // name of the port
          ocl_device::PortType(ocl_device::IOType::OUTPUT,
                               sizeof(T)), // port type
          true                             // enable stat collection
      );
    }

    OCL_MSG("LoopbackTester::Allocating input and output ports\n");

    const cl_int banks[4] = {XCL_MEM_DDR_BANK0, XCL_MEM_DDR_BANK1,
                             XCL_MEM_DDR_BANK2, XCL_MEM_DDR_BANK3};
    cl_int bank = banks[0];

    for (cl_int ix = 0; ix < width; ix++) {
      bank = banks[ix % 4];
      input_ports[ix].allocate(context, payload_size * sizeof(T), bank);
      bank = banks[(ix + width) % 4];
      output_ports[ix].allocate(context, payload_size * sizeof(T), bank);
    }

    OCL_MSG("LoopbackTester::Constructed with xclbin %s\n",
            xclbin_name.c_str());

    call_index = 0;
  }

  bool eventComplete(const cl::Event &event) const {
    cl_int state = 0;
    cl_int err = 0;
    OCL_CHECK(err, err = event.getInfo<cl_int>(
                       CL_EVENT_COMMAND_EXECUTION_STATUS, &state));
    return state == CL_COMPLETE;
  }

  bool checkKernelFinished() const {

    for (auto &input : input_ports) {
      if (input.size_event_info.active == true) {
        if (!eventComplete(input.buffer_size_event))
          return false;
      }
    }

    for (auto &output : output_ports) {
      if (output.size_event_info.active == true) {
        if (!eventComplete(output.buffer_size_event))
          return false;
      }
    }

    return true;
  }

  bool checkReadFinished() const {

    for (auto &output : output_ports) {
      if (output.buffer_event_info[0].active == true) {
        if (!eventComplete(output.buffer_event[0]))
          return false;
      }
      if (output.buffer_event_info[1].active == true) {
        if (!eventComplete(output.buffer_event[1]))
          return false;
      }
    }

    return true;
  }
  void test(bool randomize = false) {

    kernel_wait_events.clear();

    // -- write a bunch inputs, the write size is always payload_size - 1
    int arg_ix = 0;
    for (auto &input : input_ports) {
      input.device_buffer.tail = 0;
      input.device_buffer.head = payload_size - 1;
      if (randomize) {
        input.device_buffer.tail = randint(0, payload_size - 1);
        input.device_buffer.head =
            (input.device_buffer.tail + payload_size - 1) % payload_size;
      }
      input.writeToDeviceBuffer(command_queue, input.device_buffer.tail,
                                input.device_buffer.head);
      if (input.buffer_event_info[0].active == true)
        kernel_wait_events.push_back(input.buffer_event[0]);
      if (input.buffer_event_info[1].active == true)
        kernel_wait_events.push_back(input.buffer_event[1]);

      kernel.setArg(arg_ix++, input.device_buffer.data_buffer);
      kernel.setArg(arg_ix++, input.device_buffer.meta_buffer);
      kernel.setArg(arg_ix++, input.device_buffer.user_alloc_size);
      kernel.setArg(arg_ix++, input.device_buffer.head);
      kernel.setArg(arg_ix++, input.device_buffer.tail);
    }

    for (auto &output : output_ports) {
      output.device_buffer.tail = 0;
      output.device_buffer.head = 0;
      if (randomize) {
        output.device_buffer.tail = randint(0, payload_size - 1);
        output.device_buffer.head = output.device_buffer.tail;
      }
      kernel.setArg(arg_ix++, output.device_buffer.data_buffer);
      kernel.setArg(arg_ix++, output.device_buffer.meta_buffer);
      kernel.setArg(arg_ix++, output.device_buffer.user_alloc_size);
      kernel.setArg(arg_ix++, output.device_buffer.head);
      kernel.setArg(arg_ix++, output.device_buffer.tail);
    }

    cl_int err;
    OCL_CHECK(err, err = command_queue.enqueueTask(kernel, &kernel_wait_events,
                                                   &kernel_event[0]));

    for (auto &input : input_ports) {
      input.enqueueReadMeta(command_queue, &kernel_event);
    }

    for (auto &output : output_ports) {
      output.enqueueReadMeta(command_queue, &kernel_event);
    }

    OCL_MSG("LoopbackTester::kernel enqueued\n");
    call_index++;

    // now wait for the hardware
    while (checkKernelFinished() == false)
      ;

    // now enqueue the reading the output buffers
    for (auto &output : output_ports) {

      int new_head = output.host_buffer.meta_buffer[0];
      int old_head = output.device_buffer.head;

      auto produced =
          output.readFromDeviceBuffer(command_queue, old_head, new_head);
      output.device_buffer.head = new_head;
    }

    // wait for the reads to finish
    while (checkReadFinished() == false)
      ;

    // now clean up
    for (auto &input : input_ports) {
      input.releaseEvents(call_index);
    }
    for (auto &output : output_ports) {
      output.releaseEvents(call_index);
    }

    ocl_device::EventProfile kernel_profile;
    kernel_profile.getTiming(kernel_event[0]);
    kernel_profile.call_index = call_index;
    kernel_stats.push_back(kernel_profile);

    OCL_MSG("LoopbackTest::call finished\n");
    
  }

  void dumpStats(const std::string &file_name) {
    std::ofstream ofs(file_name, std::ios::out);
    std::stringstream ss;
    ss << "{" << std::endl;
    {
      ss << "\t"
         << "\"input_ports\": [" << std::endl;
      {
        for (auto input_it = input_ports.begin(); input_it != input_ports.end();
             input_it++) {
          ss << input_it->serializedStats(2);
          if (input_it != input_ports.end() - 1)
            ss << ",";
          ss << std::endl;
        }
      }
      ss << "\t"
         << "]," << std::endl;

      ss << "\t"
         << "\"kernel\": [" << std::endl;
      {
        for (auto it = this->kernel_stats.begin();
             it != this->kernel_stats.end(); it++) {
          ss << it->serialized(2);
          if (it != this->kernel_stats.end() - 1) {
            ss << ",";
          }
          ss << std::endl;
        }
      }
      ss << "\t"
         << "], " << std::endl;
      ss << "\t"
         << "\"output_ports\": [" << std::endl;
      {
        for (auto output_it = output_ports.begin();
             output_it != output_ports.end(); output_it++) {
          ss << output_it->serializedStats(2);
          if (output_it != output_ports.end() - 1)
            ss << ",";
          ss << std::endl;
        }
      }
      ss << "\t"
         << "]" << std::endl;
    }
    ss << "}";

    ofs << ss.str();
    ofs.close();
  }
};

struct TestOptions {
  bool success, randomize;
  int min_buffer, max_buffer, repeats, width;
  std::string prefix;
  void print() {
    std::cout << "=================== Options ================================="
     << std::endl;
    std::cout << "randomize: " << randomize << std::endl;
    std::cout << "width: " << width << std::endl;
    std::cout << "min_buffer: " << min_buffer << std::endl;
    std::cout << "max_buffer: " << max_buffer << std::endl;
    std::cout << "prefix: " << prefix << std::endl;
    std::cout << "repeats: " << repeats << std::endl;
    std::cout << "-------------------------------------------------------------"
     << std::endl;
  }
};

void progressBar(float new_progress, int barwidth) {
  std::cout << "[";
  int pos = new_progress * barwidth;
  for (int i = 0; i < barwidth; i++) {
    if (i < pos)
      std::cout << "=";
    else if (i == pos)
      std::cout << ">";
    else 
      std::cout << " ";
  }  
  std::cout << "] " << int(new_progress * 100) << " %\r";
  std::cout.flush();
}
TestOptions parseArguments(int argc, char *argv[]) {
  TestOptions options;
  options.success = true;
  try {
    // -- parse options
    namespace po = boost::program_options;
    int min_buffer = 0, max_buffer = 0;
    
    po::options_description desc("Allowed options");
    desc.add_options()("help", "produce help message")(
        "repeats,r", po::value<int>(&options.repeats)->default_value(1),
        "number of repeated experiments per buffer size configuration")(
        "min-buffer,m",
        po::value<int>(&options.min_buffer)->default_value(4096),
        "minimum buffer size (should be a power of two)")(
        "max-buffer,M",
        po::value<int>(&options.max_buffer)->default_value(4096),
        "maximum buffer size (should be a power of two)")(
        "prefix,p", po::value<std::string>(&options.prefix)->default_value("stats_"),
        "statistics json file prefix for each buffer size between min and "
        "max.")
        ("randomize,R", po::value<bool>(&options.randomize)->default_value(true), "randomize the head and tail pointers in each transfer")
        ("width,w", po::value<int>(&options.width)->default_value(1), "number of input and outputs to the hardware");
    po::variables_map arg_vmap;
    po::store(po::parse_command_line(argc, argv, desc), arg_vmap);

    if (arg_vmap.count("help")) {
      std::cout << desc << std::endl;
      options.success = false;
      return options;
    }

    po::notify(arg_vmap);

  } catch (std::exception &e) {
    std::cerr << "Error parsing command line arguments:\n" << e.what() << "\n";
    options.success = false;
  }
  return options;
}
int main(int argc, char *argv[]) {

  // -- parse options
  auto options = parseArguments(argc, argv);

  if (options.success == false) {
    std::cout << "exiting" << std::endl;
    return 1;
  }
  options.print();
  int barwidth = 40;
  std::stringstream kernel_name_builder;
  kernel_name_builder << "Loopback" << options.width << "_kernel";
  std::string kernel_name = kernel_name_builder.str();
  for (int buffer = options.min_buffer; buffer <= options.max_buffer; buffer <<= 1) {
    LoopbackTester<uint32_t> tester(options.width, buffer, kernel_name, "xclbin");
    std::cout << "Starting the test with buffer size " << buffer << " for " << kernel_name << std::endl;
    std::stringstream file_name;
    file_name << options.prefix << kernel_name << "_" << buffer << ".json";
    float progress = 0.0;
    for (int c = 0; c < options.repeats; c++) {
      tester.test(options.randomize);
      
      
      float new_progress = float(c) / float(options.repeats);
    
      if (new_progress - progress >= 0.05) {
        progressBar(new_progress, barwidth); 
        tester.dumpStats(file_name.str());
        progress = new_progress;
      } 
    }
    progressBar(1.0, barwidth);
    tester.dumpStats(file_name.str());
    std::cout << std::endl;
    
  }
  
}
