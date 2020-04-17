package osp.Memory;

import osp.Hardware.*;
import osp.Tasks.*;
import osp.Threads.*;
import osp.Devices.*;
import osp.Utilities.*;
import osp.IFLModules.*;

/**
 * The PageTableEntry object contains information about a specific virtual page
 * in memory, including the page frame in which it resides.
 * 
 * @OSPProject Memory
 */

public class PageTableEntry extends IflPageTableEntry {
	/**
	 * The constructor. Must call
	 * 
	 * super(ownerPageTable,pageNumber);
	 * 
	 * as its first statement.
	 * 
	 * @OSPProject Memory
	 */
	public PageTableEntry(PageTable ownerPageTable, int pageNumber) {
		// your code goes here
		// calling super
		super(ownerPageTable, pageNumber);

	}

	/**
	 * This method increases the lock count on the page by one.
	 * 
	 * The method must FIRST increment lockCount, THEN check if the page is valid,
	 * and if it is not and no page validation event is present for the page, start
	 * page fault by calling PageFaultHandler.handlePageFault().
	 * 
	 * @return SUCCESS or FAILURE FAILURE happens when the pagefault due to locking
	 *         fails or the that created the IORB thread gets killed.
	 * 
	 * @OSPProject Memory
	 */
	public int do_lock(IORB iorb) {
		// your code goes here

		// check if the page isn't valid
		if (!isValid()) {

			// check the validation event doesn't present
			if (getValidatingThread() == null) {

				// start page fault
				int PFH = PageFaultHandler.handlePageFault(iorb.getThread(), MemoryLock, this);

				// check if the pagefault fails
				if (PFH == FAILURE || iorb.getThread().getStatus() == ThreadKill) {
					return FAILURE;
				}

			}

			// check if the thread caused the page fault equal to this thread
			else if (getValidatingThread() != iorb.getThread()) {

				// suspend thread
				iorb.getThread().suspend(this);

			}

		}

		// increment lockCount
		getFrame().incrementLockCount();
		return SUCCESS;

	}

	/**
	 * This method decreases the lock count on the page by one.
	 * 
	 * This method must decrement lockCount, but not below zero.
	 * 
	 * @OSPProject Memory
	 */
	public void do_unlock() {
		// your code goes here

		// decrement lockCount if is not equal or less than 0
		if (getFrame().getLockCount() > 0) {

			getFrame().decrementLockCount();
		}
	}

	/*
	 * Feel free to add methods/fields to improve the readability of your code
	 */

}

/*
 * Feel free to add local classes to improve the readability of your code
 */
