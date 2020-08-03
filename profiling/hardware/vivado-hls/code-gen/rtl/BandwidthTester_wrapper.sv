`default_nettype none

module BandwidthTester_wrapper #(
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH = 64,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH = 32,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_AWUSER_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_ARUSER_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_WUSER_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_RUSER_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_BUSER_WIDTH =  1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH = 64,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH = 32,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_AWUSER_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_ARUSER_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_WUSER_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_RUSER_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_BUSER_WIDTH =  1
)
(
	input   wire    ap_clk,
	input   wire    ap_rst_n,
	input   wire    event_start,
	// -- write address channel
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH-1:0]     m_axi_source_Out_pass_In_AWID,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH-1:0]   m_axi_source_Out_pass_In_AWADDR,
	output wire [7:0]                         m_axi_source_Out_pass_In_AWLEN,
	output wire [2:0]                         m_axi_source_Out_pass_In_AWSIZE,
	output wire [1:0]                         m_axi_source_Out_pass_In_AWBURST,
	output wire [1:0]                         m_axi_source_Out_pass_In_AWLOCK,
	output wire [3:0]                         m_axi_source_Out_pass_In_AWCACHE,
	output wire [2:0]                         m_axi_source_Out_pass_In_AWPROT,
	output wire [3:0]                         m_axi_source_Out_pass_In_AWQOS,
	output wire [3:0]                         m_axi_source_Out_pass_In_AWREGION,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_AWUSER_WIDTH-1:0] m_axi_source_Out_pass_In_AWUSER,
	output wire                               m_axi_source_Out_pass_In_AWVALID,
	input  wire                               m_axi_source_Out_pass_In_AWREADY,
	// -- write data channel
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH-1:0]     m_axi_source_Out_pass_In_WID,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH-1:0]   m_axi_source_Out_pass_In_WDATA,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH/8-1:0] m_axi_source_Out_pass_In_WSTRB,
	output wire                               m_axi_source_Out_pass_In_WLAST,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_WUSER_WIDTH-1:0]  m_axi_source_Out_pass_In_WUSER,
	output wire                               m_axi_source_Out_pass_In_WVALID,
	input  wire                               m_axi_source_Out_pass_In_WREADY,
	// -- write response channel
	input  wire [C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH-1:0]     m_axi_source_Out_pass_In_BID,
	input  wire [1:0]                         m_axi_source_Out_pass_In_BRESP,
	input  wire [C_M_AXI_SOURCE_OUT_PASS_IN_BUSER_WIDTH-1:0]  m_axi_source_Out_pass_In_BUSER,
	input  wire                               m_axi_source_Out_pass_In_BVALID,
	output wire                               m_axi_source_Out_pass_In_BREADY,
	// -- read address channel
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH-1:0]     m_axi_source_Out_pass_In_ARID,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH-1:0]   m_axi_source_Out_pass_In_ARADDR,
	output wire [7:0]                         m_axi_source_Out_pass_In_ARLEN,
	output wire [2:0]                         m_axi_source_Out_pass_In_ARSIZE,
	output wire [1:0]                         m_axi_source_Out_pass_In_ARBURST,
	output wire [1:0]                         m_axi_source_Out_pass_In_ARLOCK,
	output wire [3:0]                         m_axi_source_Out_pass_In_ARCACHE,
	output wire [2:0]                         m_axi_source_Out_pass_In_ARPROT,
	output wire [3:0]                         m_axi_source_Out_pass_In_ARQOS,
	output wire [3:0]                         m_axi_source_Out_pass_In_ARREGION,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_ARUSER_WIDTH-1:0] m_axi_source_Out_pass_In_ARUSER,
	output wire                               m_axi_source_Out_pass_In_ARVALID,
	input  wire                               m_axi_source_Out_pass_In_ARREADY,
	// -- read data channel
	input  wire [C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH-1:0]     m_axi_source_Out_pass_In_RID,
	input  wire [C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH-1:0]   m_axi_source_Out_pass_In_RDATA,
	input  wire [1:0]                         m_axi_source_Out_pass_In_RRESP,
	input  wire                               m_axi_source_Out_pass_In_RLAST,
	input  wire [C_M_AXI_SOURCE_OUT_PASS_IN_RUSER_WIDTH-1:0]  m_axi_source_Out_pass_In_RUSER,
	input  wire                               m_axi_source_Out_pass_In_RVALID,
	output wire                               m_axi_source_Out_pass_In_RREADY,
	// -- write address channel
	output wire [C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH-1:0]     m_axi_pass_Out_sink_In_AWID,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH-1:0]   m_axi_pass_Out_sink_In_AWADDR,
	output wire [7:0]                         m_axi_pass_Out_sink_In_AWLEN,
	output wire [2:0]                         m_axi_pass_Out_sink_In_AWSIZE,
	output wire [1:0]                         m_axi_pass_Out_sink_In_AWBURST,
	output wire [1:0]                         m_axi_pass_Out_sink_In_AWLOCK,
	output wire [3:0]                         m_axi_pass_Out_sink_In_AWCACHE,
	output wire [2:0]                         m_axi_pass_Out_sink_In_AWPROT,
	output wire [3:0]                         m_axi_pass_Out_sink_In_AWQOS,
	output wire [3:0]                         m_axi_pass_Out_sink_In_AWREGION,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_AWUSER_WIDTH-1:0] m_axi_pass_Out_sink_In_AWUSER,
	output wire                               m_axi_pass_Out_sink_In_AWVALID,
	input  wire                               m_axi_pass_Out_sink_In_AWREADY,
	// -- write data channel
	output wire [C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH-1:0]     m_axi_pass_Out_sink_In_WID,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH-1:0]   m_axi_pass_Out_sink_In_WDATA,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH/8-1:0] m_axi_pass_Out_sink_In_WSTRB,
	output wire                               m_axi_pass_Out_sink_In_WLAST,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_WUSER_WIDTH-1:0]  m_axi_pass_Out_sink_In_WUSER,
	output wire                               m_axi_pass_Out_sink_In_WVALID,
	input  wire                               m_axi_pass_Out_sink_In_WREADY,
	// -- write response channel
	input  wire [C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH-1:0]     m_axi_pass_Out_sink_In_BID,
	input  wire [1:0]                         m_axi_pass_Out_sink_In_BRESP,
	input  wire [C_M_AXI_PASS_OUT_SINK_IN_BUSER_WIDTH-1:0]  m_axi_pass_Out_sink_In_BUSER,
	input  wire                               m_axi_pass_Out_sink_In_BVALID,
	output wire                               m_axi_pass_Out_sink_In_BREADY,
	// -- read address channel
	output wire [C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH-1:0]     m_axi_pass_Out_sink_In_ARID,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH-1:0]   m_axi_pass_Out_sink_In_ARADDR,
	output wire [7:0]                         m_axi_pass_Out_sink_In_ARLEN,
	output wire [2:0]                         m_axi_pass_Out_sink_In_ARSIZE,
	output wire [1:0]                         m_axi_pass_Out_sink_In_ARBURST,
	output wire [1:0]                         m_axi_pass_Out_sink_In_ARLOCK,
	output wire [3:0]                         m_axi_pass_Out_sink_In_ARCACHE,
	output wire [2:0]                         m_axi_pass_Out_sink_In_ARPROT,
	output wire [3:0]                         m_axi_pass_Out_sink_In_ARQOS,
	output wire [3:0]                         m_axi_pass_Out_sink_In_ARREGION,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_ARUSER_WIDTH-1:0] m_axi_pass_Out_sink_In_ARUSER,
	output wire                               m_axi_pass_Out_sink_In_ARVALID,
	input  wire                               m_axi_pass_Out_sink_In_ARREADY,
	// -- read data channel
	input  wire [C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH-1:0]     m_axi_pass_Out_sink_In_RID,
	input  wire [C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH-1:0]   m_axi_pass_Out_sink_In_RDATA,
	input  wire [1:0]                         m_axi_pass_Out_sink_In_RRESP,
	input  wire                               m_axi_pass_Out_sink_In_RLAST,
	input  wire [C_M_AXI_PASS_OUT_SINK_IN_RUSER_WIDTH-1:0]  m_axi_pass_Out_sink_In_RUSER,
	input  wire                               m_axi_pass_Out_sink_In_RVALID,
	output wire                               m_axi_pass_Out_sink_In_RREADY,
	// -- SDx Control signals
	input  wire    [32 - 1 : 0]    source_Out_pass_In_requested_size,
	input  wire    [64 - 1 : 0]    source_Out_pass_In_size,
	input  wire    [64 - 1 : 0]    source_Out_pass_In_buffer,
	input  wire    [32 - 1 : 0]    pass_Out_sink_In_available_size,
	input  wire    [64 - 1 : 0]    pass_Out_sink_In_size,
	input  wire    [64 - 1 : 0]    pass_Out_sink_In_buffer,
	// -- kernel command
	input  wire    [64 - 1 : 0] kernel_command,
	input   wire    ap_start,
	output  wire    ap_ready,
	output  wire    ap_idle,
	output  wire    ap_done
);

	timeunit 1ps;
	timeprecision 1ps;

	// -- --------------------------------------------------------------------------
	// -- Wires and Variables
	// -- --------------------------------------------------------------------------

	// -- AP Control
	logic   ap_start_r = 1'b0;

	// -- Network I/O for BandwidthTester module
	wire    [31:0] source_Out_pass_In_din;
	wire    source_Out_pass_In_full_n;
	wire    source_Out_pass_In_write;
	wire    [31:0] source_Out_pass_In_fifo_count;
	wire    [31:0] source_Out_pass_In_fifo_size;
	wire    [63:0] source_Out_pass_In_offset;
	wire    [31:0] pass_Out_sink_In_dout;
	wire    pass_Out_sink_In_empty_n;
	wire    pass_Out_sink_In_read;
	wire    [31:0] pass_Out_sink_In_fifo_count;
	wire    [31:0] pass_Out_sink_In_fifo_size;
	wire    [63:0] pass_Out_sink_In_offset;
	wire    BandwidthTester_ap_idle;
	wire    BandwidthTester_ap_done;
	// -- AP for I/O Stage
	wire    source_Out_pass_In_input_stage_ap_start;
	wire    source_Out_pass_In_input_stage_ap_done;
	wire    source_Out_pass_In_input_stage_ap_idle;
	wire    [31:0] source_Out_pass_In_input_stage_ap_return;

	wire    input_stage_idle;
	wire    input_stage_done;

	wire    pass_Out_sink_In_output_stage_ap_start;
	wire    pass_Out_sink_In_output_stage_ap_done;
	wire    pass_Out_sink_In_output_stage_ap_idle;
	wire    [31:0] pass_Out_sink_In_output_stage_ap_return;

	wire    output_stage_idle;
	wire    output_stage_done;

	// -- idle registers
	logic   input_stage_idle_r = 1'b1;
	logic   output_stage_idle_r = 1'b1;
	logic   BandwidthTester_idle_r = 1'b1;
	logic   ap_idle_r = 1'b1;

	// -- local trigger wire
	wire    source_Out_pass_In_sleep;
	wire    source_Out_pass_In_sync_wait;
	wire    source_Out_pass_In_sync_exec;
	wire    source_Out_pass_In_waited;
	wire    source_Out_pass_In_all_waited;
	wire    pass_Out_sink_In_sleep;
	wire    pass_Out_sink_In_sync_wait;
	wire    pass_Out_sink_In_sync_exec;
	wire    pass_Out_sink_In_waited;
	wire    pass_Out_sink_In_all_waited;
	// -- global trigger wires

	// -- global trigger signals
	wire    all_sleep;
	wire    all_sync;
	wire    all_sync_wait;
	wire    external_enqueue;

	// -- --------------------------------------------------------------------------
	// -- Begin RTL Body
	// -- --------------------------------------------------------------------------

	// -- AP control logic
	localparam [1:0] KERNEL_IDLE = 2'b00;
	localparam [1:0] KERNEL_START = 2'b01;
	localparam [1:0] KERNEL_DONE = 2'b10;
	localparam [1:0] KERNEL_ERROR = 2'b11;
	logic    [1:0] ap_state = KERNEL_IDLE;
	always_ff @(posedge ap_clk) begin
		 if(ap_rst_n == 1'b0) begin
			ap_state <= 2'b0;
		end
		else begin
			case (ap_state)
				KERNEL_IDLE	: ap_state <= (ap_start) ? KERNEL_START : KERNEL_IDLE;
				KERNEL_START: ap_state <= (input_stage_idle && BandwidthTester_ap_idle && output_stage_idle) ? KERNEL_DONE : KERNEL_START;
				KERNEL_DONE	: ap_state <= KERNEL_IDLE;
				KERNEL_ERROR: ap_state <= KERNEL_IDLE;
			endcase
		end
	end
	assign ap_idle = ap_state == KERNEL_IDLE;
	assign ap_done = ap_state == KERNEL_DONE;
	assign ap_ready = ap_state == KERNEL_DONE;

	// -- input stage idle signal
	assign input_stage_idle = source_Out_pass_In_input_stage_ap_idle;

	// -- output stage idle signal
	assign output_stage_idle = pass_Out_sink_In_output_stage_ap_idle;

	// -- offset logic
	assign source_Out_pass_In_offset = 64'b0;

	assign pass_Out_sink_In_offset = 64'b0;


	// -- Input stage for port : source_Out_pass_In

	assign source_Out_pass_In_input_stage_ap_start = event_start;

	source_Out_pass_In_input_stage #(
		.C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_AWUSER_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_AWUSER_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_ARUSER_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_ARUSER_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_WUSER_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_WUSER_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_RUSER_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_RUSER_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_BUSER_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_BUSER_WIDTH )
	)
	i_source_Out_pass_In_input_stage(
		.ap_clk(ap_clk),
		.ap_rst_n(ap_rst_n),
		.ap_start(source_Out_pass_In_input_stage_ap_start),
		.ap_done(source_Out_pass_In_input_stage_ap_done),
		.ap_idle(source_Out_pass_In_input_stage_ap_idle),
		.ap_ready(),
		.ap_return(source_Out_pass_In_input_stage_ap_return),
		.m_axi_source_Out_pass_In_AWVALID(m_axi_source_Out_pass_In_AWVALID),
		.m_axi_source_Out_pass_In_AWREADY(m_axi_source_Out_pass_In_AWREADY),
		.m_axi_source_Out_pass_In_AWADDR(m_axi_source_Out_pass_In_AWADDR),
		.m_axi_source_Out_pass_In_AWID(m_axi_source_Out_pass_In_AWID),
		.m_axi_source_Out_pass_In_AWLEN(m_axi_source_Out_pass_In_AWLEN),
		.m_axi_source_Out_pass_In_AWSIZE(m_axi_source_Out_pass_In_AWSIZE),
		.m_axi_source_Out_pass_In_AWBURST(m_axi_source_Out_pass_In_AWBURST),
		.m_axi_source_Out_pass_In_AWLOCK(m_axi_source_Out_pass_In_AWLOCK),
		.m_axi_source_Out_pass_In_AWCACHE(m_axi_source_Out_pass_In_AWCACHE),
		.m_axi_source_Out_pass_In_AWPROT(m_axi_source_Out_pass_In_AWPROT),
		.m_axi_source_Out_pass_In_AWQOS(m_axi_source_Out_pass_In_AWQOS),
		.m_axi_source_Out_pass_In_AWREGION(m_axi_source_Out_pass_In_AWREGION),
		.m_axi_source_Out_pass_In_AWUSER(m_axi_source_Out_pass_In_AWUSER),
		.m_axi_source_Out_pass_In_WVALID(m_axi_source_Out_pass_In_WVALID),
		.m_axi_source_Out_pass_In_WREADY(m_axi_source_Out_pass_In_WREADY),
		.m_axi_source_Out_pass_In_WDATA(m_axi_source_Out_pass_In_WDATA),
		.m_axi_source_Out_pass_In_WSTRB(m_axi_source_Out_pass_In_WSTRB),
		.m_axi_source_Out_pass_In_WLAST(m_axi_source_Out_pass_In_WLAST),
		.m_axi_source_Out_pass_In_WID(m_axi_source_Out_pass_In_WID),
		.m_axi_source_Out_pass_In_WUSER(m_axi_source_Out_pass_In_WUSER),
		.m_axi_source_Out_pass_In_ARVALID(m_axi_source_Out_pass_In_ARVALID),
		.m_axi_source_Out_pass_In_ARREADY(m_axi_source_Out_pass_In_ARREADY),
		.m_axi_source_Out_pass_In_ARADDR(m_axi_source_Out_pass_In_ARADDR),
		.m_axi_source_Out_pass_In_ARID(m_axi_source_Out_pass_In_ARID),
		.m_axi_source_Out_pass_In_ARLEN(m_axi_source_Out_pass_In_ARLEN),
		.m_axi_source_Out_pass_In_ARSIZE(m_axi_source_Out_pass_In_ARSIZE),
		.m_axi_source_Out_pass_In_ARBURST(m_axi_source_Out_pass_In_ARBURST),
		.m_axi_source_Out_pass_In_ARLOCK(m_axi_source_Out_pass_In_ARLOCK),
		.m_axi_source_Out_pass_In_ARCACHE(m_axi_source_Out_pass_In_ARCACHE),
		.m_axi_source_Out_pass_In_ARPROT(m_axi_source_Out_pass_In_ARPROT),
		.m_axi_source_Out_pass_In_ARQOS(m_axi_source_Out_pass_In_ARQOS),
		.m_axi_source_Out_pass_In_ARREGION(m_axi_source_Out_pass_In_ARREGION),
		.m_axi_source_Out_pass_In_ARUSER(m_axi_source_Out_pass_In_ARUSER),
		.m_axi_source_Out_pass_In_RVALID(m_axi_source_Out_pass_In_RVALID),
		.m_axi_source_Out_pass_In_RREADY(m_axi_source_Out_pass_In_RREADY),
		.m_axi_source_Out_pass_In_RDATA(m_axi_source_Out_pass_In_RDATA),
		.m_axi_source_Out_pass_In_RLAST(m_axi_source_Out_pass_In_RLAST),
		.m_axi_source_Out_pass_In_RID(m_axi_source_Out_pass_In_RID),
		.m_axi_source_Out_pass_In_RUSER(m_axi_source_Out_pass_In_RUSER),
		.m_axi_source_Out_pass_In_RRESP(m_axi_source_Out_pass_In_RRESP),
		.m_axi_source_Out_pass_In_BVALID(m_axi_source_Out_pass_In_BVALID),
		.m_axi_source_Out_pass_In_BREADY(m_axi_source_Out_pass_In_BREADY),
		.m_axi_source_Out_pass_In_BRESP(m_axi_source_Out_pass_In_BRESP),
		.m_axi_source_Out_pass_In_BID(m_axi_source_Out_pass_In_BID),
		.m_axi_source_Out_pass_In_BUSER(m_axi_source_Out_pass_In_BUSER),

		.source_Out_pass_In_requested_size(source_Out_pass_In_requested_size),
		.source_Out_pass_In_size_r(source_Out_pass_In_size),
		.source_Out_pass_In_buffer(source_Out_pass_In_buffer),
		.kernel_command(kernel_command[31:0]),
		.source_Out_pass_In_offset(source_Out_pass_In_offset),
		.all_sync(all_sync),
		.all_sync_wait(all_sync_wait),
		.all_sleep(all_sleep),
		.all_waited(source_Out_pass_In_all_waited),
		.source_Out_pass_In_sleep(source_Out_pass_In_sleep),
		.source_Out_pass_In_sync_wait(source_Out_pass_In_sync_wait),
		.source_Out_pass_In_sync_exec(source_Out_pass_In_sync_exec),
		.source_Out_pass_In_waited(source_Out_pass_In_waited),
		.source_Out_pass_In_din(source_Out_pass_In_din),
		.source_Out_pass_In_full_n(source_Out_pass_In_full_n),
		.source_Out_pass_In_write(source_Out_pass_In_write),
		.source_Out_pass_In_fifo_count(source_Out_pass_In_fifo_count),
		.source_Out_pass_In_fifo_size(source_Out_pass_In_fifo_size)
	);

	BandwidthTester i_BandwidthTester(
		//-- Streaming ports
		.source_Out_pass_In_din(source_Out_pass_In_din),
		.source_Out_pass_In_full_n(source_Out_pass_In_full_n),
		.source_Out_pass_In_write(source_Out_pass_In_write),
		.source_Out_pass_In_fifo_count(source_Out_pass_In_fifo_count),
		.source_Out_pass_In_fifo_size(source_Out_pass_In_fifo_size),
		// -- trigger signals
		.source_Out_pass_In_sleep(source_Out_pass_In_sleep),
		.source_Out_pass_In_sync_wait(source_Out_pass_In_sync_wait),
		.source_Out_pass_In_sync_exec(source_Out_pass_In_sync_exec),
		.source_Out_pass_In_waited(source_Out_pass_In_waited),
		.source_Out_pass_In_all_waited(source_Out_pass_In_all_waited),
		//-- Streaming ports
		.pass_Out_sink_In_dout(pass_Out_sink_In_dout),
		.pass_Out_sink_In_empty_n(pass_Out_sink_In_empty_n),
		.pass_Out_sink_In_read(pass_Out_sink_In_read),
		.pass_Out_sink_In_fifo_count(pass_Out_sink_In_fifo_count),
		.pass_Out_sink_In_fifo_size(pass_Out_sink_In_fifo_size),
		// -- trigger wires
		.pass_Out_sink_In_sleep(pass_Out_sink_In_sleep),
		.pass_Out_sink_In_sync_wait(pass_Out_sink_In_sync_wait),
		.pass_Out_sink_In_sync_exec(pass_Out_sink_In_sync_exec),
		.pass_Out_sink_In_waited(pass_Out_sink_In_waited),
		.pass_Out_sink_In_all_waited(pass_Out_sink_In_all_waited),
		//-- global trigger signals
		.all_sync(all_sync),
		.all_sync_wait(all_sync_wait),
		.all_sleep(all_sleep),
		//-- AP control
		.ap_clk(ap_clk),
		.ap_rst_n(ap_rst_n),
		.ap_start(event_start),
		.ap_idle(BandwidthTester_ap_idle),
		.ap_done(BandwidthTester_ap_done)
	);
	// -- Output stage for port : pass_Out_sink_In

	assign pass_Out_sink_In_output_stage_ap_start = event_start;

	pass_Out_sink_In_output_stage #(
		.C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH ),
		.C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH ),
		.C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH ),
		.C_M_AXI_PASS_OUT_SINK_IN_AWUSER_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_AWUSER_WIDTH ),
		.C_M_AXI_PASS_OUT_SINK_IN_ARUSER_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_ARUSER_WIDTH ),
		.C_M_AXI_PASS_OUT_SINK_IN_WUSER_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_WUSER_WIDTH ),
		.C_M_AXI_PASS_OUT_SINK_IN_RUSER_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_RUSER_WIDTH ),
		.C_M_AXI_PASS_OUT_SINK_IN_BUSER_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_BUSER_WIDTH )
	)
	i_pass_Out_sink_In_output_stage(
		.ap_clk(ap_clk),
		.ap_rst_n(ap_rst_n),
		.ap_start(pass_Out_sink_In_output_stage_ap_start),
		.ap_done(pass_Out_sink_In_output_stage_ap_done),
		.ap_idle(pass_Out_sink_In_output_stage_ap_idle),
		.ap_ready(),
		.ap_return(pass_Out_sink_In_output_stage_ap_return),
		.m_axi_pass_Out_sink_In_AWVALID(m_axi_pass_Out_sink_In_AWVALID),
		.m_axi_pass_Out_sink_In_AWREADY(m_axi_pass_Out_sink_In_AWREADY),
		.m_axi_pass_Out_sink_In_AWADDR(m_axi_pass_Out_sink_In_AWADDR),
		.m_axi_pass_Out_sink_In_AWID(m_axi_pass_Out_sink_In_AWID),
		.m_axi_pass_Out_sink_In_AWLEN(m_axi_pass_Out_sink_In_AWLEN),
		.m_axi_pass_Out_sink_In_AWSIZE(m_axi_pass_Out_sink_In_AWSIZE),
		.m_axi_pass_Out_sink_In_AWBURST(m_axi_pass_Out_sink_In_AWBURST),
		.m_axi_pass_Out_sink_In_AWLOCK(m_axi_pass_Out_sink_In_AWLOCK),
		.m_axi_pass_Out_sink_In_AWCACHE(m_axi_pass_Out_sink_In_AWCACHE),
		.m_axi_pass_Out_sink_In_AWPROT(m_axi_pass_Out_sink_In_AWPROT),
		.m_axi_pass_Out_sink_In_AWQOS(m_axi_pass_Out_sink_In_AWQOS),
		.m_axi_pass_Out_sink_In_AWREGION(m_axi_pass_Out_sink_In_AWREGION),
		.m_axi_pass_Out_sink_In_AWUSER(m_axi_pass_Out_sink_In_AWUSER),
		.m_axi_pass_Out_sink_In_WVALID(m_axi_pass_Out_sink_In_WVALID),
		.m_axi_pass_Out_sink_In_WREADY(m_axi_pass_Out_sink_In_WREADY),
		.m_axi_pass_Out_sink_In_WDATA(m_axi_pass_Out_sink_In_WDATA),
		.m_axi_pass_Out_sink_In_WSTRB(m_axi_pass_Out_sink_In_WSTRB),
		.m_axi_pass_Out_sink_In_WLAST(m_axi_pass_Out_sink_In_WLAST),
		.m_axi_pass_Out_sink_In_WID(m_axi_pass_Out_sink_In_WID),
		.m_axi_pass_Out_sink_In_WUSER(m_axi_pass_Out_sink_In_WUSER),
		.m_axi_pass_Out_sink_In_ARVALID(m_axi_pass_Out_sink_In_ARVALID),
		.m_axi_pass_Out_sink_In_ARREADY(m_axi_pass_Out_sink_In_ARREADY),
		.m_axi_pass_Out_sink_In_ARADDR(m_axi_pass_Out_sink_In_ARADDR),
		.m_axi_pass_Out_sink_In_ARID(m_axi_pass_Out_sink_In_ARID),
		.m_axi_pass_Out_sink_In_ARLEN(m_axi_pass_Out_sink_In_ARLEN),
		.m_axi_pass_Out_sink_In_ARSIZE(m_axi_pass_Out_sink_In_ARSIZE),
		.m_axi_pass_Out_sink_In_ARBURST(m_axi_pass_Out_sink_In_ARBURST),
		.m_axi_pass_Out_sink_In_ARLOCK(m_axi_pass_Out_sink_In_ARLOCK),
		.m_axi_pass_Out_sink_In_ARCACHE(m_axi_pass_Out_sink_In_ARCACHE),
		.m_axi_pass_Out_sink_In_ARPROT(m_axi_pass_Out_sink_In_ARPROT),
		.m_axi_pass_Out_sink_In_ARQOS(m_axi_pass_Out_sink_In_ARQOS),
		.m_axi_pass_Out_sink_In_ARREGION(m_axi_pass_Out_sink_In_ARREGION),
		.m_axi_pass_Out_sink_In_ARUSER(m_axi_pass_Out_sink_In_ARUSER),
		.m_axi_pass_Out_sink_In_RVALID(m_axi_pass_Out_sink_In_RVALID),
		.m_axi_pass_Out_sink_In_RREADY(m_axi_pass_Out_sink_In_RREADY),
		.m_axi_pass_Out_sink_In_RDATA(m_axi_pass_Out_sink_In_RDATA),
		.m_axi_pass_Out_sink_In_RLAST(m_axi_pass_Out_sink_In_RLAST),
		.m_axi_pass_Out_sink_In_RID(m_axi_pass_Out_sink_In_RID),
		.m_axi_pass_Out_sink_In_RUSER(m_axi_pass_Out_sink_In_RUSER),
		.m_axi_pass_Out_sink_In_RRESP(m_axi_pass_Out_sink_In_RRESP),
		.m_axi_pass_Out_sink_In_BVALID(m_axi_pass_Out_sink_In_BVALID),
		.m_axi_pass_Out_sink_In_BREADY(m_axi_pass_Out_sink_In_BREADY),
		.m_axi_pass_Out_sink_In_BRESP(m_axi_pass_Out_sink_In_BRESP),
		.m_axi_pass_Out_sink_In_BID(m_axi_pass_Out_sink_In_BID),
		.m_axi_pass_Out_sink_In_BUSER(m_axi_pass_Out_sink_In_BUSER),

		.pass_Out_sink_In_available_size(pass_Out_sink_In_available_size),
		.pass_Out_sink_In_size_r(pass_Out_sink_In_size),
		.pass_Out_sink_In_buffer(pass_Out_sink_In_buffer),
		.kernel_command(kernel_command[63:32]),
		.pass_Out_sink_In_offset(pass_Out_sink_In_offset),
		.all_sync(all_sync),
		.all_sync_wait(all_sync_wait),
		.all_sleep(all_sleep),
		.all_waited(pass_Out_sink_In_all_waited),
		.pass_Out_sink_In_sleep(pass_Out_sink_In_sleep),
		.pass_Out_sink_In_sync_wait(pass_Out_sink_In_sync_wait),
		.pass_Out_sink_In_sync_exec(pass_Out_sink_In_sync_exec),
		.pass_Out_sink_In_waited(pass_Out_sink_In_waited),
		.pass_Out_sink_In_dout(pass_Out_sink_In_dout),
		.pass_Out_sink_In_empty_n(pass_Out_sink_In_empty_n),
		.pass_Out_sink_In_read(pass_Out_sink_In_read),
		.pass_Out_sink_In_fifo_count(pass_Out_sink_In_fifo_count),
		.pass_Out_sink_In_fifo_size(pass_Out_sink_In_fifo_size)
	);

endmodule : BandwidthTester_wrapper
`default_nettype wire
