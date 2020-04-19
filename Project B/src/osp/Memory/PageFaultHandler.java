package osp.Memory;

import java.util.*;
import osp.Hardware.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.FileSys.FileSys;
import osp.FileSys.OpenFile;
import osp.IFLModules.*;
import osp.Interrupts.*;
import osp.Utilities.*;
import osp.IFLModules.*;

/**
 * Purpose: The page fault handler is responsible for handling a page fault. If
 * a swap in or swap out operation is required, the page fault handler must
 * request the operation.
 * 
 * @OSPProject Memory
 * 
 *             Authors: Abdulaziz Hasan 1555528, Mohammed Shukri 1647376 Date of
 *             the Last modification: 16/4/2020
 */
public class PageFaultHandler extends IflPageFaultHandler {

	/**
	 * Purpose: This method handles a page fault.
	 * 
	 * Inputs: - thread the thread that requested a page fault - referenceType
	 * whether it is memory read or write - page the memory page
	 * 
	 * Output: - Return SUCCESS if everything is fine. - FAILURE if the thread dies
	 * while waiting for swap in or swap out or if the page is already in memory and
	 * no page fault was necessary (well, this shouldn't happen, but...). In
	 * addition, if there is no frame that can be allocated to satisfy the page
	 * fault, then it should return NotEnoughMemory
	 * 
	 * 
	 * @OSPProject Memory
	 * 
	 *             Authors: Abdulaziz Hasan 1555528, Mohammed Shukri 1647376 Date of
	 *             the Last modification: 17/4/2020
	 */
	public static int do_handlePageFault(ThreadCB thread, int referenceType, PageTableEntry page) {
//		 Check and return FAILURE if the page is valid,
		if (page.isValid()) {
			return FAILURE;
		}

//		 Creating a new empty frame. 
		FrameTableEntry NFrame = null;
//		 Searching for the first free frame, starting the search from frame[0].
//		 The new frame found will be stored in the variable "NFrame"
		NFrame = getFreeFrame();

//		 Check if the frame is still empty
		if (NFrame == null) {

			NFrame = SecondChance();

			if (NFrame == null) {

				return NotEnoughMemory;
			}
		}

//			 If the frame is still empty after performing SecondChance; return "NotEnoughMemory".

//		 Set the validating thread of the page to input thread.
		page.setValidatingThread(thread);
//		 Create a new event: "event" of type SystemEvent() 
//		 The event object is saved in the variable "event", because when pagefault handling 
//		 is finished the thread will be resumed by
//		 executing notifyThreads() on that event.
		Event event = new SystemEvent("PageFaultHappend");
//	 	 Suspend the thread. 
		thread.suspend(event);

// 		 Checking if the frame is not reserved nor locked
		if (!NFrame.isReserved() && NFrame.getLockCount() <= 0) {

//			Protect the frame from theft by reserving the frame.
			NFrame.setReserved(thread.getTask());

		}
//		If the frame contains a dirty page, then swap-out will
//		be performed, followed by freeing the frame.
		PageTableEntry Npage = NFrame.getPage();
		if (Npage != null) {

			if (NFrame.isDirty()) {
//				Swap-out
				NFrame.getPage().getTask().getSwapFile().write(NFrame.getPage().getID(), NFrame.getPage(), thread);
//				The thread that caused the pagefault can be killed by the simulator at any moment after
//				the thread goes to sleep waiting for the swap-out to complete.
//				FAILURE is returned in that case	
				if (thread.getStatus() == GlobalVariables.ThreadKill) {
					page.notifyThreads();
					event.notifyThreads();
					ThreadCB.dispatch();
					return FAILURE;

				}
//				Setting the frame's dirty bit to false = not dirty = clean. 
				NFrame.setDirty(false);

			}
//			Freeing the frame:
//			Dereferencing the frame.
//			System.out.println("2");
			NFrame.setReferenced(false);
//			 Check if the page is not empty and the frame is not locked
			if (Npage != null ) {

//	  		    Setting the page's validity bit to 0
				Npage.setValid(false);
//				Emptying the page's frame by setting it to null frame.
				Npage.setFrame(null);
//				Emptying the frame by setting it to no page (null page) 
				NFrame.setPage(null);

			}

		}
//		Setting the page's frame to the new frame
		page.setFrame(NFrame);
		NFrame.setPage(page);
//		Swap-in
		page.getTask().getSwapFile().read(page.getID(), page, thread);
//		The thread that caused the pagefault can be killed by the simulator
//		at any moment after the thread goes to sleep waiting for the swap-in to complete.
//		FAILURE is returned in that case	
		if (thread.getStatus() == ThreadKill) {
			page.setValidatingThread(null);
			page.setFrame(null);
			page.notifyThreads();

//			if (NFrame.getReserved() == thread.getTask()) {
//				NFrame.setUnreserved(thread.getTask());
//			}
			NFrame.setReferenced(false);
			NFrame.setDirty(false);
			event.notifyThreads();
			NFrame.setPage(null);
			ThreadCB.dispatch();
			return FAILURE;
		}

//		Setting the validity bit to true
		page.setValid(true);
//		Unreserving the frame if its still reserved
		if (NFrame.getReserved() == thread.getTask()) {
			NFrame.setUnreserved(thread.getTask());
		}
		NFrame.setReferenced(true);
		page.notifyThreads();
		event.notifyThreads();
//		Setting the frame's dirty bit to true if the reference type is MemoryWrite, else unset the dirty bit
		if (referenceType == MemoryWrite) {
			NFrame.setDirty(true);
		} else {
			NFrame.setDirty(false);
		}
//		Finalizing by clearing the page's validity bit, notifying the threads then dispatching. 
		page.setValidatingThread(null);
		page.notifyThreads();
		event.notifyThreads();
		ThreadCB.dispatch();
		return SUCCESS;
	}

	/**
	 * Purpose: Calculate the current number of free frames. It does not matter
	 * where the search in the frame table starts. Note: this method will not change
	 * the value of the reference bits, dirty bits or MMU.Cursor.
	 * 
	 * Output: Integer of type (int) represnting the current number of free frames.
	 * 
	 * @OSPProject Memory
	 * 
	 *             Authors: Abdulaziz Hasan 1555528, Mohammed Shukri 1647376 Date of
	 *             the Last modification: 16/4/2020
	 */
	static int numFreeFrames() {

		int curentFreeFrames = 0;
		FrameTableEntry frame;
		for (int i = 0; i < MMU.getFrameTableSize(); i++) {
			frame = MMU.getFrame(i);
			if ((frame.getPage() == null) && (!frame.isReserved()) && (frame.getLockCount() == 0) && frame != null) {
				curentFreeFrames++;
			}
		}
		return curentFreeFrames;

	}

	/**
	 * Purpose: Looks for a free frame; returns the first free frame starting the
	 * search from frame[0].
	 * 
	 * Output: Returns a frame of type FrameTableEntry.
	 * 
	 * @OSPProject Memory
	 * 
	 *             Authors: Abdulaziz Hasan 1555528, Mohammed Shukri 1647376 Date of
	 *             the Last modification: 17/4/2020
	 */

	static FrameTableEntry getFreeFrame() {

		FrameTableEntry frame = null;
		int i = 0;
		while (i < MMU.getFrameTableSize()) {
			frame = MMU.getFrame(i);
			if ((frame.getPage() == null) && (!frame.isReserved()) && (frame.getLockCount() == 0) && frame != null) {
				return frame;

			}
			i++;
		}
		return null;

	}

	/**
	 * Purpose: Frees frames using the Second Chance approach. The search uses the
	 * MMU variable MMU.Cursor to specify the starting frame index of the search.
	 * 
	 * Output: Returns a frame of type FrameTableEntry.
	 * 
	 * @OSPProject Memory
	 * 
	 *             Authors: Abdulaziz Hasan 1555528, Mohammed Shukri 1647376 Date of
	 *             the Last modification: 17/4/2020
	 */
	static FrameTableEntry SecondChance() {
		FrameTableEntry frame;
		boolean isdirty = true;
		int frameID = 0;
		int counter = 0;
		int x = MMU.getFrameTableSize() * 2;
		// Phase I - Batch freeing of occupied frames that are clean.

		while (counter < x && numFreeFrames() < MMU.wantFree) {
			frame = MMU.getFrame(MMU.Cursor);
			// 1. If a page's reference bit is set, clear it and move to the next frame
			if (frame.isReferenced()) {
				frame.setReferenced(false);
				// MMU.Cursor++; // or could try frame = MMU.getFrame(MMU.Cursor + 1);
			}
			// 2. Finding a clean frame; i.e. a frame containing a page and whose reference
			// bit is
			// not set, and the frame is not locked and not reserved and not dirty.
			else if (frame.getPage() != null && frame.isReferenced() == false && frame.getLockCount() == 0
					&& frame.isReserved() == false && frame.isDirty() == false && numFreeFrames() <= MMU.wantFree) {
				// a. freeing the frame
				// b. Updating a page table

				// set validity flag to false
				frame.getPage().setValid(false);
				// set frame entry to null
				frame.getPage().setFrame(null);
				// free frame
				frame.setPage(null);
				// didn't clean the page
				frame.setDirty(false);
				// didn't unset the reference bit
				// frame.setReferenced(false);
				

			}
			if (frame.getLockCount() == 0 && !frame.isReserved() && frame.isDirty() && isdirty) {
				frameID = frame.getID();
				isdirty = false;
			}
			MMU.Cursor = (MMU.Cursor + 1) % MMU.getFrameTableSize();
			counter++;
		}

		/*- Phase II - Skip if the number of free frames is wantFree, otherwise do the following: */

		if (numFreeFrames() != MMU.wantFree) {

//			If the number of free frames from Phase I is less than wantFree and we did
//			not come across any dirty frames
			if (numFreeFrames() < MMU.wantFree && isdirty) {
				System.out.println("isDirst == True");
//				Invoking getFreeFrame() to get a free frame. 
				FrameTableEntry freeFrame = getFreeFrame();
//				Return the free frame.
				return freeFrame;
			}
			return MMU.getFrame(frameID);

		}

		/* Phase III - Phase one managed to free "wantFree" frames */
		else {

//				invoking getFreeFrame() to get a free frame.
			FrameTableEntry freeFrame = getFreeFrame();
//				Returning the free frame.
			return freeFrame;
		}

		// Return null if no appropriate frame is found.

	}

	/**
	 * Purpose: Frees frames using the Second Chance approach. The search uses the
	 * MMU variable MMU.Cursor to specify the starting frame index of the search.
	 * 
	 * Output: Returns a frame of type FrameTableEntry.
	 * 
	 * @OSPProject Memory
	 * 
	 *             Authors: Abdulaziz Hasan 1555528, Mohammed Shukri 1647376 Date of
	 *             the Last modification: 18/4/2020
	 */
	static FrameTableEntry Fifo() {
		MyOut.print("osp.Memory.PageFaultHandler", "Starting FIFO");
		FrameTableEntry frame;
		int counter = 0;

		// Phase I - Batch freeing of occupied frames that are clean.

		while ((counter > (MMU.getFrameTableSize())) && (numFreeFrames() <= MMU.wantFree)) {

			frame = MMU.getFrame(MMU.Cursor);

			// 2. Finding a clean frame; i.e. a frame containing a page and whose reference
			// bit is
			// not set, and the frame is not locked and not reserved and not dirty.
			if (frame.getPage() != null && frame.isReferenced() == false && frame.getLockCount() == 0
					&& frame.isReserved() == false && frame.isDirty() == false) {
				// a. freeing the frame
				// b. Updating a page table
				frameFreeing(frame);
			}

			MMU.Cursor = (MMU.Cursor + 1) % MMU.getFrameTableSize();
			counter++;

		}
		/* Phase II - Phase one managed to free "wantFree" frames */
		if (numFreeFrames() == MMU.wantFree) {

//					invoking getFreeFrame() to get a free frame.
			FrameTableEntry freeFrame = getFreeFrame();
//					Returning the free frame.
			return freeFrame;

		}
		// Return null if no appropriate frame is found.
		return null;

	}

	static FrameTableEntry frameFreeing(FrameTableEntry frame) {

		// a. freeing the frame
		frame.setPage(null);
		frame.setDirty(false);
		frame.setReferenced(false);
		// b. Updating a page table
		frame.getPage().setFrame(null);
		frame.getPage().setValid(false);
		return frame;
	}

	static Event reserve(ThreadCB thread, PageTableEntry page, FrameTableEntry frame) {
//		Create a new event: "event" of type SystemEvent() 
//		The event object is saved in the variable "event", because when pagefault handling 
//		is finished the thread will be resumed by
//		executing notifyThreads() on that event.
		Event event = new SystemEvent("PageFaultHappend");
//	 	Suspend the thread. 
		thread.suspend(event);
//		Set the validating thread of the page to input thread.
		page.setValidatingThread(thread);
//		Protect the frame from theft by reserving the frame.
		frame.setReserved(thread.getTask());
		return event;
	}

}

/*
 * Feel free to add local classes to improve the readability of your code
 */