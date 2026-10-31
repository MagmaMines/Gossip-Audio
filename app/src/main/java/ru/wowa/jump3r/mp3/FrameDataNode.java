package ru.wowa.jump3r.mp3;


public class FrameDataNode {
  FrameDataNode nxt;
	/**
	 * Frame Identifier
	 */
	int fid;
	/**
	 * 3-character language descriptor
	 */
	String lng;

	Inf dsc = new Inf(), txt = new Inf();
}
