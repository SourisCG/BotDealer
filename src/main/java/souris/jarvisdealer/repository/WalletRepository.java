package souris.jarvisdealer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import souris.jarvisdealer.model.Wallet;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long>{

}
