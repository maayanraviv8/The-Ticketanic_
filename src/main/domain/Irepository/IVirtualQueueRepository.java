package domain.Irepository;

import domain.Aggregates.virtualqueue.VirtualQueue;

public interface IVirtualQueueRepository extends IRepository<VirtualQueue> {
    VirtualQueue findById(String showId);
}
