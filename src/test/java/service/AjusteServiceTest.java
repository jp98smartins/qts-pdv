package service;

import net.originmobi.pdv.enumerado.EntradaSaida;
import net.originmobi.pdv.enumerado.ajuste.AjusteStatus;
import net.originmobi.pdv.exceptions.ajuste.*;
import net.originmobi.pdv.filter.AjusteFilter;
import net.originmobi.pdv.model.AjusteProduto;
import net.originmobi.pdv.model.Ajuste;
import net.originmobi.pdv.model.Produto;
import net.originmobi.pdv.repository.AjusteRepository;
import net.originmobi.pdv.service.AjusteService;
import net.originmobi.pdv.service.ProdutoService;
import net.originmobi.pdv.singleton.Aplicacao;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AjusteServiceTest {
    private Page<Ajuste> pageAjuste;

    private Aplicacao aplicacao;

    private Pageable pageable;

    private AjusteFilter filter;

    private ProdutoService produtoService;

    private AjusteRepository ajusteRepository;

    private AjusteService ajusteService;

    @Before
    public void init() {
        pageAjuste = Page.empty();
        pageable = Pageable.unpaged();
        filter = mock(AjusteFilter.class);
        produtoService = mock(ProdutoService.class);
        ajusteRepository = mock(AjusteRepository.class);
        aplicacao = mock(Aplicacao.class);
        ajusteService = new AjusteService(ajusteRepository, produtoService, aplicacao);
    }

    @Test
    public void listaReturnListOfAjusteWhenCodeIsPassed() {
        // Arrange
        final Long code = 12345L;
        when(filter.getCodigo()).thenReturn(code);
        when(ajusteRepository.lista(code, pageable)).thenReturn(pageAjuste);

        // Act
        final Page<Ajuste> result = ajusteService.lista(pageable, filter);

        // Assert
        Assert.assertEquals(result, pageAjuste);
    }

    @Test
    public void listaReturnListOfAjusteWhenCodeIsNotPassed() {
        // Arrange
        when(filter.getCodigo()).thenReturn(null);
        when(ajusteRepository.lista(pageable)).thenReturn(pageAjuste);

        // Act
        final Page<Ajuste> result = ajusteService.lista(pageable, filter);

        // Assert
        Assert.assertEquals(result, pageAjuste);
    }

    @Test
    public void buscaReturnAjusteWhenValidCodeIsPassed() {
        // Arrange
        final Optional<Ajuste> ajuste = Optional.of(mock(Ajuste.class));
        when(ajusteRepository.findById(anyLong())).thenReturn(ajuste);

        // Act
        final Optional<Ajuste> result = ajusteService.busca(123456L);

        // Assert
        Assert.assertEquals(result, ajuste);
    }

    @Test
    public void buscaReturnNothingWhenInvalidCodeIsPassed() {
        // Arrange
        final Optional<Ajuste> ajuste = Optional.empty();
        when(ajusteRepository.findById(anyLong())).thenReturn(ajuste);

        // Act
        final Optional<Ajuste> result = ajusteService.busca(123456L);

        // Assert
        Assert.assertEquals(result, ajuste);
    }

    @Test
    public void novoReturnNewAjusteCode() {
        // Arrange
        final Long mockCode = 123456L;
        final Ajuste ajuste = mock(Ajuste.class);
        when(ajuste.getCodigo()).thenReturn(mockCode);
        when(ajusteRepository.save(any())).thenReturn(ajuste);
        when(aplicacao.getUsuarioAtual()).thenReturn("USER");

        // Act
        final Long result = ajusteService.novo();

        // Assert
        Assert.assertEquals(result, mockCode);
    }

    @Test
    public void removerThrowAlreadyProcessedExceptionWhenAjusteHasProcessadoStatus() {
        // Arrange
        final Ajuste ajuste = mock(Ajuste.class);
        when(ajuste.getStatus()).thenReturn(AjusteStatus.PROCESSADO);

        // Act
        final AlreadyProcessedException exception = assertThrows(AlreadyProcessedException.class, () -> ajusteService.remover(ajuste));

        // Assert
        Assert.assertEquals("O ajuste já esta processado", exception.getMessage());
    }

    @Test
    public void removerThrowUnableToDeleteWhenAnExceptionHasOccurred() {
        // Arrange
        final Ajuste ajuste = mock(Ajuste.class);
        when(ajuste.getStatus()).thenReturn(AjusteStatus.APROCESSAR);
        when(ajuste.getCodigo()).thenThrow(new RuntimeException());

        // Act
        final UnableToDeleteException exception = assertThrows(UnableToDeleteException.class, () -> ajusteService.remover(ajuste));

        // Assert
        Assert.assertEquals("Erro ao tentar cancelar o ajuste", exception.getMessage());
    }

    @Test
    public void removerCompletesWhenAjusteWasDeletedSuccessfully() {
        // Arrange
        final Ajuste ajuste = mock(Ajuste.class);
        when(ajuste.getStatus()).thenReturn(AjusteStatus.APROCESSAR);
        when(ajuste.getCodigo()).thenReturn(123456L);
        doNothing().when(ajusteRepository).deleteById(any());

        // Act
        ajusteService.remover(ajuste);

        assertDoesNotThrow(() -> ajusteService.remover(ajuste));
    }

    @Test
    public void processarThrowAjusteNotFoundExceptionWhenAjusteWasNotFound() {
        // Arrange
        final Long code = 12345L;
        final String obs = "";
        when(ajusteRepository.findById(anyLong())).thenReturn(Optional.empty());

        // Act
        final AjusteNotFoundException exception = assertThrows(AjusteNotFoundException.class, () -> ajusteService.processar(code, obs));

        // Assert
        Assert.assertEquals("Ajuste não encontrado", exception.getMessage());
    }

    @Test
    public void processarThrowAlreadyProcessedExceptionWhenAjusteHasProcessadoStatus() {
        // Arrange
        final Long code = 12345L;
        final String obs = "";
        final Ajuste ajuste = mock(Ajuste.class);
        when(ajuste.getStatus()).thenReturn(AjusteStatus.PROCESSADO);
        when(ajusteRepository.findById(anyLong())).thenReturn(Optional.of(ajuste));

        // Act
        final AlreadyProcessedException exception = assertThrows(AlreadyProcessedException.class, () -> ajusteService.processar(code, obs));

        // Assert
        Assert.assertEquals("Ajuste já processado", exception.getMessage());
    }

    @Test
    public void processarThrowUnableToChangeStockExceptionWhenAnExceptionHasOccurred() {
        // Arrange
        final Long code = 12345L;
        final String obs = "";
        final Ajuste ajuste = new Ajuste();
        final Produto produto = new Produto();
        produto.setCodigo(12345L);
        final AjusteProduto ajusteProduto = new AjusteProduto();
        ajusteProduto.setProduto(produto);
        ajusteProduto.setQtd_alteracao(123);
        final List<AjusteProduto> list = new ArrayList<>();
        list.add(ajusteProduto);
        ajuste.setProdutos(list);
        ajuste.setStatus(AjusteStatus.APROCESSAR);
        doThrow(new RuntimeException()).when(produtoService).ajusteEstoque(anyLong(), anyInt(), any(EntradaSaida.class), anyString(), any());
        when(ajusteRepository.findById(anyLong())).thenReturn(Optional.of(ajuste));

        // Act
        final UnableToChangeStockException exception = assertThrows(UnableToChangeStockException.class, () -> ajusteService.processar(code, obs));

        // Assert
        Assert.assertEquals("Erro ao tentar processar o ajuste, chame o suporte", exception.getMessage());
    }

    @Test
    public void processarThrowUnableToSaveExceptionWhenAnExceptionHasOccurred() {
        // Arrange
        final Long code = 12345L;
        final String obs = "";
        final List<AjusteProduto> list = new ArrayList<>();
        final Ajuste ajuste = mock(Ajuste.class);
        when(ajuste.getStatus()).thenReturn(AjusteStatus.APROCESSAR);
        when(ajuste.getProdutos()).thenReturn(list);
        when(ajusteRepository.findById(anyLong())).thenReturn(Optional.of(ajuste));
        when(ajusteRepository.save(any())).thenThrow(new RuntimeException());

        // Act
        final UnableToSaveException exception = assertThrows(UnableToSaveException.class, () -> ajusteService.processar(code, obs));

        // Assert
        Assert.assertEquals("Erro ao tentar processar o ajuste, chame o suporte", exception.getMessage());
    }

    @Test
    public void processarChangeStatusObservationAndProcessDateWhenCompletesSuccessfullyEntrada() {
        // Arrange
        final ArgumentCaptor<EntradaSaida> entradaSaidaArgumentCaptor = ArgumentCaptor.forClass(EntradaSaida.class);
        final ArgumentCaptor<String> origemOperacaoArgumentCaptor = ArgumentCaptor.forClass(String.class);
        final Long code = 12345L;
        final String obs = "";
        final Ajuste ajuste = new Ajuste();
        final Produto produto = new Produto();
        produto.setCodigo(12345L);
        final AjusteProduto ajusteProduto = new AjusteProduto();
        ajusteProduto.setProduto(produto);
        ajusteProduto.setQtd_alteracao(123);
        final List<AjusteProduto> list = new ArrayList<>();
        list.add(ajusteProduto);
        ajuste.setProdutos(list);
        ajuste.setStatus(AjusteStatus.APROCESSAR);
        doNothing().when(produtoService).ajusteEstoque(anyLong(), anyInt(), any(EntradaSaida.class), anyString(), any());
        when(ajusteRepository.findById(anyLong())).thenReturn(Optional.of(ajuste));
        when(ajusteRepository.save(any())).thenReturn(ajuste);

        // Act
        final String result = ajusteService.processar(code, obs);

        // Assert
        assertEquals("Ajuste realizado com sucesso", result);
        verify(produtoService).ajusteEstoque(anyLong(), anyInt(), entradaSaidaArgumentCaptor.capture(), origemOperacaoArgumentCaptor.capture(), any());
        assertEquals("Referente ao ajuste de estoque " + code, origemOperacaoArgumentCaptor.getValue());
        assertEquals(EntradaSaida.ENTRADA, entradaSaidaArgumentCaptor.getValue());
    }

    @Test
    public void processarChangeStatusObservationAndProcessDateWhenCompletesSuccessfullySaida() {
        // Arrange
        final ArgumentCaptor<EntradaSaida> entradaSaidaArgumentCaptor = ArgumentCaptor.forClass(EntradaSaida.class);
        final ArgumentCaptor<String> origemOperacaoArgumentCaptor = ArgumentCaptor.forClass(String.class);
        final Long code = 12345L;
        final String obs = "";
        final Ajuste ajuste = new Ajuste();
        final Produto produto = new Produto();
        produto.setCodigo(12345L);
        final AjusteProduto ajusteProduto = new AjusteProduto();
        ajusteProduto.setProduto(produto);
        ajusteProduto.setQtd_alteracao(0);
        final List<AjusteProduto> list = new ArrayList<>();
        list.add(ajusteProduto);
        ajuste.setProdutos(list);
        ajuste.setStatus(AjusteStatus.APROCESSAR);
        doNothing().when(produtoService).ajusteEstoque(anyLong(), anyInt(), any(EntradaSaida.class), anyString(), any());
        when(ajusteRepository.findById(anyLong())).thenReturn(Optional.of(ajuste));
        when(ajusteRepository.save(any())).thenReturn(ajuste);

        // Act
        final String result = ajusteService.processar(code, obs);

        // Assert
        assertEquals("Ajuste realizado com sucesso", result);
        verify(produtoService).ajusteEstoque(anyLong(), anyInt(), entradaSaidaArgumentCaptor.capture(), origemOperacaoArgumentCaptor.capture(), any());
        assertEquals("Referente ao ajuste de estoque " + code, origemOperacaoArgumentCaptor.getValue());
        assertEquals(EntradaSaida.SAIDA, entradaSaidaArgumentCaptor.getValue());
    }

    @Test
    public void processarChangeStatusObservationAndProcessDateWhenCompletesSuccessfully() {
        final Long code = 12345L;
        final String obs = "";
        final Ajuste ajuste = new Ajuste();
        final Produto produto = new Produto();
        produto.setCodigo(12345L);
        final AjusteProduto ajusteProduto = new AjusteProduto();
        ajusteProduto.setProduto(produto);
        ajusteProduto.setQtd_alteracao(0);
        final List<AjusteProduto> list = new ArrayList<>();
        list.add(ajusteProduto);
        ajuste.setProdutos(list);
        ajuste.setStatus(AjusteStatus.APROCESSAR);
        doNothing().when(produtoService).ajusteEstoque(anyLong(), anyInt(), any(EntradaSaida.class), anyString(), any());
        when(ajusteRepository.findById(anyLong())).thenReturn(Optional.of(ajuste));
        when(ajusteRepository.save(any())).thenReturn(ajuste);

        // Act
        final String result = ajusteService.processar(code, obs);

        // Assert
        assertEquals("Ajuste realizado com sucesso", result);
    }
}
