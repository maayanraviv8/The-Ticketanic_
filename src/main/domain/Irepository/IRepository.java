package domain.Irepository;

import java.util.List;

public interface IRepository<T> {
    void store(T entity);

    void delete(String id);

    T findById(String id);

    List<T> getAll();
}
