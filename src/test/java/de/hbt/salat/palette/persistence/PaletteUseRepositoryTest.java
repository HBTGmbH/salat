package de.hbt.salat.palette.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.palette.domain.PaletteUse;
import de.hbt.salat.palette.domain.PaletteUseDay;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The runs of the palette per login and day against the database (#1501). */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class PaletteUseRepositoryTest {

  private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

  @Autowired
  private PaletteUseRepository repository;

  @Autowired
  private SalatUserRepository salatUserRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private SalatUser login;

  @BeforeEach
  void setUp() {
    var user = new SalatUser();
    user.setLoginname("pu");
    user.setStatus("ma");
    login = salatUserRepository.save(user);
  }

  @Test
  void the_increment_finds_no_row_before_the_first_run_and_raises_it_after() {
    assertThat(repository.increment(login.getId(), DAY)).isZero();

    var use = new PaletteUse();
    use.setSalatUser(login);
    use.setUseDate(DAY);
    use.setUseCount(1);
    repository.saveAndFlush(use);

    assertThat(repository.increment(login.getId(), DAY)).isEqualTo(1);
    assertThat(repository.increment(login.getId(), DAY)).isEqualTo(1);
    assertThat(repository.sumUses(login.getId())).isEqualTo(3);
    assertThat(repository.findAllDays()).containsExactly(new PaletteUseDay(login.getId(), DAY, 3));
  }

  @Test
  void a_login_without_runs_has_none() {
    assertThat(repository.sumUses(login.getId())).isZero();
  }
}
