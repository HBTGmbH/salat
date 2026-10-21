package de.hbt.salat.tag.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.tag.domain.Tag;

@Repository
public interface TagRepository extends PagingAndSortingRepository<Tag, Long>, CrudRepository<Tag, Long> {

  List<Tag> findAllByNameKeyIn(Collection<String> nameKeys);

  List<Tag> findAllByOrderByNameKeyAsc();
}
