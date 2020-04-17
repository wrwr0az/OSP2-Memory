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
		
		// check if page valid return FAILURE
		if (page.isValid()) {
			return FAILURE;
		}
		
		// create object new frame take initial value null
		FrameTableEntry NFrame = null;
		
		// new frame value is a free frame
		NFrame = getFreeFrame();

		// if there isn't free frame
		if (NFrame == null) {
			
			// do second chance
			NFrame = SecondChance();
			
			// if free frame still null return not Enough Memory
			if (NFrame == null)
				return NotEnoughMemory;
		}

		// set validating thread
		page.setValidatingThread(thread);
		
		// create object event 
		Event event = new SystemEvent("PageFaultHappend");
		// Suspend thread on system event
		thread.suspend(event);

		// if new frame not not reserved and lock count less than 1
		if (!NFrame.isReserved() && NFrame.getLockCount() <= 0) {

			// set reserved new frame to thead task
			NFrame.setReserved(thread.getTask());


		}

		// create object new page take a frame page
		PageTableEntry Npage = NFrame.getPage();
		
		// if new page not null
		if (Npage != null) {

			// if new frame is dirty
			if (NFrame.isDirty()) {

				// Swap out
				NFrame.getPage().getTask().getSwapFile().write(NFrame.getPage().getID(), NFrame.getPage(), thread);

				// if the thread killed resume thread and dispatch then return FAILURE
				if (thread.getStatus() == GlobalVariables.ThreadKill) {
					
					// Resume all page thread
					page.notifyThreads();
					// Resume all event thread
					event.notifyThreads();
					// dispatch a thread
					ThreadCB.dispatch();
					// return FAILURE
					return FAILURE;

				}

				// set new frame dirty to false
				NFrame.setDirty(false);

			}


			// set new frame referenced to false 
			NFrame.setReferenced(false);
			
			// if new page isn't null and unlocked
			if (Npage != null && Npage.getFrame().getLockCount() == 0) {
				
				// set new frame page to null (free frame)
				NFrame.setPage(null);
				// set new page valid to false
				Npage.setValid(false);
				// new page frame to null
				Npage.setFrame(null);

			}

		}

		// set frame to page equal to new frame
		page.setFrame(NFrame);
		// set page for new frame
	    NFrame.setPage(page);
		// swap in
		page.getTask().getSwapFile().read(page.getID(), page, thread);

		// if the thread killed
		if (thread.getStatus() == ThreadKill) {

			// set page validating to null
			page.setValidatingThread(null);
			// set page frame to null
			page.setFrame(null);
			// Resume page thread
			page.notifyThreads();

			// if new frame reserved equal to thread task set unreserved
			if (NFrame.getReserved() == thread.getTask()) {
				
				// set unreserved for new frame to thread task
				NFrame.setUnreserved(thread.getTask());
			}

			// set referenced for new frame to false
			NFrame.setReferenced(false);
			// set dirty for new frame to false
			NFrame.setDirty(false);
			// Resume event thread
			event.notifyThreads();
			// free frame 
			NFrame.setPage(null);
			// dispatch a thread
			ThreadCB.dispatch();
			// return FAILURE
			return FAILURE;
		}

		// set page valid to true
		page.setValid(true);
		
		// if new frame reserved equal to thread task
		if (NFrame.getReserved() == thread.getTask()) {
			
			// set unreserved for new frame to thread task
			NFrame.setUnreserved(thread.getTask());
		}
		
		// set new frame referenced to true 
		NFrame.setReferenced(true);
		// Resume page thread
		page.notifyThreads();
		// Resume event thread 
		event.notifyThreads();

		// if referenced type is memory write
		if (referenceType == MemoryWrite) {
			
			// set dirty for new frame to true
			NFrame.setDirty(true);
		} 
		
		else {
			// set it to false
			NFrame.setDirty(false);
		}

		// set page validating to null
		page.setValidatingThread(null);
		// Dispatch a thread
		ThreadCB.dispatch();
		// return SUCCESS
		return SUCCESS;
	}

	static int numFreeFrames() {

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

	// Looks for a free frame; returns the first free frame starting the search from
	// frame[0].
	static FrameTableEntry getFreeFrame() {
		
		// create a frame
		FrameTableEntry frame = null;
		// create variable i 
		int i = 0;
		// while i less than table size
		while (i < MMU.getFrameTableSize()) {
			// get frame
			frame = MMU.getFrame(i);
			
			// if frame not reserved and unlocked
			if ((!frame.isReserved() && frame.getLockCount() == 0)) {
				// break from while
				break;
			}
			// increment i 
			i++;
		}
		// return frame
		return frame;

	}

	static FrameTableEntry SecondChance() {
		
		// create frame
		FrameTableEntry frame;
		// to get first dirty frame
		boolean isdirty = true;
		// create frameID to save ID of frame
		int frameID = 0;
		// create counter
		int counter = 0;

		// Phase I - Batch freeing of occupied frames the clean.
		// check if the counter is less than 2 multiply by frame table size
		while (counter > (2 * MMU.getFrameTableSize())) {
		
			// check if number of free frame 
			if (numFreeFrames() < MMU.wantFree) {

				// get frame from MMU cursor
				frame = MMU.getFrame(MMU.Cursor);

				// 1. If a page's reference bit is set, clear it and move to the next frame
				// check if frame is referenced
				if (frame.isReferenced()) {
					// set reference false
					frame.setReferenced(false);
					// increment cursor
					MMU.Cursor++; // or could try frame = MMU.getFrame(MMU.Cursor + 1);
				}

				// 2. Finding a clean frame
				
				// if frame not referenced and not reserved and not dirty and unlocked and number of free frame less than or equal to MMU wantFree
				if (frame.isReferenced() == false && frame.isReserved() == false && frame.isDirty() == false
						&& frame.getLockCount() == 0) {
					
					// a. freeing the frame
					frame.setPage(null);
					// didn't clean the page
					frame.setDirty(false);
					// didn't unset the reference bit
					frame.setReferenced(false);
					
					// b. Updating a page table
					// set frame entry to null
					frame.getPage().setFrame(null);
					// set validity flag to false
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
		}
		

		/*- Phase II - Skip if the number of free frames is wantFree, otherwise do the following: */

		// check if number of free frame not equal to MMU wantFree
		if (numFreeFrames() != MMU.wantFree) {
			
			// check if is dirty false ,, means there is dirty frame
			if (!isdirty)
				// return first dirty frame
				return new FrameTableEntry(frameID);

			// if number of free frame less than MMU wantFree and is dirty is true ,, means there is not dirty frame
			if (numFreeFrames() < MMU.wantFree) {
				// invoke method getfreeframe to get the free frame and save it
				FrameTableEntry freeFrame = getFreeFrame();
				// retrun the free frame
				return freeFrame;
			}

		}
		
		/* Phase III - Phase one managed to free "wantFree" frames */
		else {
			// if number of free frame equal to MMU wantFree
			if (numFreeFrames() == MMU.wantFree) {
				// return frame from method getFreeFrame()
				FrameTableEntry freeFrame = getFreeFrame();
				// return free frame
				return freeFrame;
			}
		}

		// return null
		return null;

	}

	/*
	 * Feel free to add methods/fields to improve the readability of your code
	 */

}

/*
 * Feel free to add local classes to improve the readability of your code
 */