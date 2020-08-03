`default_nettype none
`timescale 1 ns / 1 ps

module BandwidthTester_kernel #(
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH = 64,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH = 512,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_AWUSER_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_ARUSER_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_WUSER_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_RUSER_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_BUSER_WIDTH =  1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH = 64,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH = 512,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_AWUSER_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_ARUSER_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_WUSER_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_RUSER_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_BUSER_WIDTH =  1,
	parameter integer C_S_AXI_CONTROL_ADDR_WIDTH = 7,
	parameter integer C_S_AXI_CONTROL_DATA_WIDTH = 32
)
(
	input   wire    ap_clk,
	input   wire    ap_rst_n,
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
	// -- AXI4-Lite slave interface
	input   wire    s_axi_control_AWVALID,
	output  wire    s_axi_control_AWREADY,
	input   wire    [C_S_AXI_CONTROL_ADDR_WIDTH-1:0]    s_axi_control_AWADDR,
	input   wire    s_axi_control_WVALID,
	output  wire    s_axi_control_WREADY,
	input   wire    [C_S_AXI_CONTROL_DATA_WIDTH-1:0]    s_axi_control_WDATA,
	input   wire    [C_S_AXI_CONTROL_DATA_WIDTH/8-1:0]  s_axi_control_WSTRB,
	input   wire    s_axi_control_ARVALID,
	output  wire    s_axi_control_ARREADY,
	input   wire    [C_S_AXI_CONTROL_ADDR_WIDTH-1:0]    s_axi_control_ARADDR,
	output  wire    s_axi_control_RVALID,
	input   wire    s_axi_control_RREADY,
	output  wire    [C_S_AXI_CONTROL_DATA_WIDTH-1:0]    s_axi_control_RDATA,
	output  wire    [2-1:0] s_axi_control_RRESP,
	output  wire    s_axi_control_BVALID,
	input   wire    s_axi_control_BREADY,
	output  wire    [2-1:0] s_axi_control_BRESP,
	output  wire    interrupt
);

// -- --------------------------------------------------------------------------
// -- Reg & Wires
// -- --------------------------------------------------------------------------

(* DONT_TOUCH = "yes" *)
reg     areset = 1'b0;
wire    ap_start;
wire    ap_ready;
wire    ap_idle;
wire    ap_done;
wire    event_start;
wire    [32 - 1 : 0] source_Out_pass_In_requested_size;
wire    [64 - 1 : 0] source_Out_pass_In_size;
wire    [64 - 1 : 0] source_Out_pass_In_buffer;
wire    [32 - 1 : 0] pass_Out_sink_In_available_size;
wire    [64 - 1 : 0] pass_Out_sink_In_size;
wire    [64 - 1 : 0] pass_Out_sink_In_buffer;

// -- kernel command
wire    [64 - 1 : 0] kernel_command;

// -- Invert reset signal
always @(posedge ap_clk) begin
	 areset <= ~ap_rst_n;
end

// -- --------------------------------------------------------------------------
// -- AXI4-Lite Control
// -- --------------------------------------------------------------------------

BandwidthTester_control_s_axi #(
	.C_S_AXI_ADDR_WIDTH ( C_S_AXI_CONTROL_ADDR_WIDTH ),
	.C_S_AXI_DATA_WIDTH ( C_S_AXI_CONTROL_DATA_WIDTH )
)
inst_control_s_axi (
	.ACLK(ap_clk),
	.ARESET(areset),
	.ACLK_EN(1'b1),
	.AWVALID(s_axi_control_AWVALID),
	.AWREADY(s_axi_control_AWREADY),
	.AWADDR(s_axi_control_AWADDR),
	.WVALID(s_axi_control_WVALID),
	.WREADY(s_axi_control_WREADY),
	.WDATA(s_axi_control_WDATA),
	.WSTRB(s_axi_control_WSTRB),
	.ARVALID(s_axi_control_ARVALID),
	.ARREADY(s_axi_control_ARREADY),
	.ARADDR(s_axi_control_ARADDR),
	.RVALID(s_axi_control_RVALID),
	.RREADY(s_axi_control_RREADY),
	.RDATA(s_axi_control_RDATA),
	.RRESP(s_axi_control_RRESP),
	.BVALID(s_axi_control_BVALID),
	.BREADY(s_axi_control_BREADY),
	.BRESP(s_axi_control_BRESP),
	.source_Out_pass_In_requested_size( source_Out_pass_In_requested_size ),
	.source_Out_pass_In_size( source_Out_pass_In_size ),
	.source_Out_pass_In_buffer( source_Out_pass_In_buffer ),
	.pass_Out_sink_In_available_size( pass_Out_sink_In_available_size ),
	.pass_Out_sink_In_size( pass_Out_sink_In_size ),
	.pass_Out_sink_In_buffer( pass_Out_sink_In_buffer ),
	.kernel_command(kernel_command),
	.interrupt( interrupt ),
	.ap_start( ap_start ),
	.ap_done( ap_done ),
	.ap_ready( ap_ready ),
	.ap_idle( ap_idle ),
	.event_start(event_start)
);

// -- --------------------------------------------------------------------------
// -- Kernel Wrapper
// -- --------------------------------------------------------------------------

BandwidthTester_wrapper #(
	.C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH(C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH),
	.C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH(C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH),
	.C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH(C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH),
	.C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH(C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH)
)
inst_wrapper (
	.ap_clk( ap_clk ),
	.ap_rst_n( ap_rst_n ),
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
	.source_Out_pass_In_requested_size( source_Out_pass_In_requested_size ),
	.source_Out_pass_In_size( source_Out_pass_In_size ),
	.source_Out_pass_In_buffer( source_Out_pass_In_buffer ),
	.pass_Out_sink_In_available_size( pass_Out_sink_In_available_size ),
	.pass_Out_sink_In_size( pass_Out_sink_In_size ),
	.pass_Out_sink_In_buffer( pass_Out_sink_In_buffer ),
	.kernel_command(kernel_command),
	.ap_start( ap_start ),
	.ap_done( ap_done),
	.ap_ready( ap_ready),
	.ap_idle( ap_idle ),
	.event_start(event_start)
);

endmodule
`default_nettype wire
