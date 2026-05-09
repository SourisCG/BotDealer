package souris.jarvisdealer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import souris.jarvisdealer.model.Founds;

@Repository
public interface FoundsRepository extends JpaRepository<Founds, Long> {

}
