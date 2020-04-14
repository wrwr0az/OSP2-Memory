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
 * The page fault handler is responsible for handling a page fault. If a swap in
 * or swap out operation is required, the page fault handler must request the
 * operation.
 * 
 * @OSPProject Memory
 */
public class PageFaultHandler extends IflPageFaultHandler {
	/**
	 * This method handles a page fault.
	 * 
	 * It must check and return if the page is valid,
	 * 
	 * It must check if the page is already being brought in by some other thread,
	 * i.e., if the page has already pagefaulted (for instance, using
	 * getValidatingThread()). If that is the case, the thread must be suspended on
	 * that page.
	 * 
	 * If none of the above is true, a new frame must be chosen and reserved until
	 * the swap in of the requested page into this frame is complete.
	 * 
	 * Note that you have to make sure that the validating thread of a page is set
	 * correctly. To this end, you must set the page's validating thread using
	 * setValidatingThread() when a pagefault happens and you must set it back to
	 * null when the pagefault is over.
	 * 
	 * If no free frame could be found, then a page replacement algorithm must be
	 * used to select a victim page to be replaced.
	 * 
	 * If a swap-out is necessary (because the chosen frame is dirty), the victim
	 * page must be dissasociated from the frame and marked invalid. After the
	 * swap-in, the frame must be marked clean. The swap-ins and swap-outs must be
	 * preformed using regular calls to read() and write().
	 * 
	 * The student implementation should define additional methods, e.g, a method to
	 * search for an available frame, and a method to select a victim page making
	 * its frame available.
	 * 
	 * Note: multiple threads might be waiting for completion of the page fault. The
	 * thread that initiated the pagefault would be waiting on the IORBs that are
	 * tasked to bring the page in (and to free the frame during the swapout).
	 * However, while pagefault is in progress, other threads might request the same
	 * page. Those threads won't cause another pagefault, of course, but they would
	 * enqueue themselves on the page (a page is also an Event!), waiting for the
	 * completion of the original pagefault. It is thus important to call
	 * notifyThreads() on the page at the end -- regardless of whether the pagefault
	 * succeeded in bringing the page in or not.
	 * 
	 * @param thread        the thread that requested a page fault
	 * @param referenceType whether it is memory read or write
	 * @param page          the memory page
	 * 
	 * @return SUCCESS is everything is fine; FAILURE if the thread dies while
	 *         waiting for swap in or swap out or if the page is already in memory
	 *         and no page fault was necessary (well, this shouldn't happen,
	 *         but...). In addition, if there is no frame that can be allocated to
	 *         satisfy the page fault, then it should return NotEnoughMemory
	 * 
	 * @OSPProject Memory
	 */
	public static int do_handlePageFault(ThreadCB thread, int referenceType, PageTableEntry page) {
		// your code goes here
		if (page.isValid()) {
			return FAILURE;
		}

		FrameTableEntry NFrame = getFreeFrame();
		if (NFrame == null) {
			return NotEnoughMemory;
		}

		Event event = new SystemEvent("PageFaultHappend");
		thread.suspend(event);

		PageTableEntry Npage = NFrame.getPage();
		if (Npage != null) {

			if (NFrame.isDirty()) {

				NFrame.getPage().getTask().getSwapFile().write(NFrame.getPage().getID(), NFrame.getPage(), thread);

				if (thread.getStatus() == GlobalVariables.ThreadKill) {
					page.notifyThreads();

					event.notifyThreads();
					ThreadCB.dispatch();
					return FAILURE;

				}

				NFrame.setDirty(false);

			}

			NFrame.setReferenced(false);
			NFrame.setPage(null);
			Npage.setValid(false);
			Npage.setFrame(null);

		}

		page.setFrame(NFrame);
		page.getTask().getSwapFile().read(page.getID(), page, thread);

		if (thread.getStatus() == ThreadKill) {

			if (NFrame.getPage() != null) {

				if (NFrame.getPage().getTask() == thread.getTask()) {

					NFrame.setPage(null);
				}
			}

			page.notifyThreads();
			page.setValidatingThread(null);
			page.setFrame(null);

			event.notifyThreads();
			ThreadCB.dispatch();
			return FAILURE;
		}

		NFrame.setPage(page);
		page.setValid(true);

		if (NFrame.getReserved() == thread.getTask()) {
			NFrame.setUnreserved(thread.getTask());
		}

		page.setValidatingThread(null);

		page.notifyThreads();
		event.notifyThreads();
		ThreadCB.dispatch();
		return SUCCESS;
	}

	int numFreeFrames() {

		// create variable to count free frame
		int curentFreeFrames = 0;
		// create a frame
		FrameTableEntry frame;
		// Search for all frame
		for (int i = 0; i < MMU.getFrameTableSize(); i++) {
			frame = MMU.getFrame(i);
			// if frame page not null and frame isn't reserved and frame is unlocked and frame is not null
			if ((frame.getPage() == null) && (!frame.isReserved()) && (frame.getLockCount() == 0) && frame != null) {
				// increment count of free frame by 1
				curentFreeFrames++;
			}
		}
		// return number of free frame
		return curentFreeFrames;

	}

	static FrameTableEntry getFreeFrame() {
		// create variable to count free frame
		int curentFreeFrames = 0;
		// create a frame
		FrameTableEntry frame = null;
		// Search for all frame
		b: for (int i = 0; i < MMU.getFrameTableSize(); i++) {
			frame = MMU.getFrame(i);
			//// if frame page not null and frame isn't reserved and frame is unlocked and frame is not null
			if ((frame.getPage() == null) && (!frame.isReserved()) && (frame.getLockCount() == 0) && frame != null) {
				// break the loop to return first free frame
				break b;
			}

		}
		// return first free frame
		return frame;

	}

	FrameTableEntry SecondChance() {
		// create frame
		FrameTableEntry frame;
		// to get first dirty frame
		boolean isdirty = true;
		// create frameID to save ID of frame
		int frameID = 0;
		// create counter
		int counter = 0;

		// check if the counter is less than 2 multiply by frame table size
		while (counter > (2 * MMU.getFrameTableSize())) {
			// get frame from MMU cursor
			frame = MMU.getFrame(MMU.Cursor);
			// check if frame is referenced
			if (frame.isReferenced()) {
				// set reference false
				frame.setReferenced(false);
			}

			// if frame not referenced and not reserved and not dirst and unlocked and
			// number of free frame less than or equal to MMU wantFree
			if (frame.isReferenced() == false && frame.isReserved() == false && frame.isDirty() == false
					&& frame.getLockCount() == 0 && numFreeFrames() <= MMU.wantFree) {

				// free frame 
				frame.setPage(null);
				// didn't clean the page
				frame.setDirty(false);
				// didn't unset the reference bit
				frame.setReferenced(false);
				// set frame entry to null
				frame.getPage().setFrame(null);
				// set validity flag  to false
				frame.getPage().setValid(false);

			}
			// check if frame dirty and unlocked and is not reserved,, also isdirty is true to get first dirty frame
			if (frame.isDirty() && isdirty && frame.getLockCount() == 0 && frame.isReserved() == false) {
				// copy frame id
				frameID = frame.getID();
				// change isdirty to false 
				isdirty = false;
			}
			// update MMU cursor 
			MMU.Cursor = (MMU.Cursor + 1) % MMU.getFrameTableSize();
			// increment counter
			counter++;
		}

		/*
		 * if (numFreeFrames() != MMU.wantFree && !isdirty) { return frameID; } if
		 * (numFreeFrames() < MMU.wantFree && isdirty) { FrameTableEntry freeFrame =
		 * getFreeFrame(); return freeFrame; }
		 */

		// check if number of free frame not equal to MMU wantFree
		if (numFreeFrames() != MMU.wantFree) {
			// check if is dirty false ,, means there is dirty frame
			if (!isdirty)
				// return first dirty frame
				return new FrameTableEntry(frameID);
			// if number of free frame less than MMU wantFree and is dirty is true ,, means there is not dirty frame
			if (numFreeFrames() < MMU.wantFree && isdirty) {
				// invoke method getfreeframe to get the free frame and save it 
				FrameTableEntry freeFrame = getFreeFrame();
				// retrun the free frame
				return freeFrame;
			}

		}

		else {
			// if number of free frame equal to MMU wantFree 
			if (numFreeFrames() == MMU.wantFree) {
				// return frame from method getFreeFrame()  
				return getFreeFrame();
			}
		}

		return null;

	}

	/*
	 * Feel free to add methods/fields to improve the readability of your code
	 */

}

/*
 * Feel free to add local classes to improve the readability of your code
 */
