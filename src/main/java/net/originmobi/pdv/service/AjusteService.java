package net.originmobi.pdv.service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import net.originmobi.pdv.enumerado.EntradaSaida;
import net.originmobi.pdv.enumerado.ajuste.AjusteStatus;
import net.originmobi.pdv.exceptions.ajuste.*;
import net.originmobi.pdv.filter.AjusteFilter;
import net.originmobi.pdv.model.Ajuste;
import net.originmobi.pdv.repository.AjusteRepository;
import net.originmobi.pdv.singleton.Aplicacao;

@Service
public class AjusteService {
	public AjusteService(AjusteRepository ajustes, ProdutoService produtos, Aplicacao aplicacao) {
		this.ajustes = ajustes;
		this.produtos = produtos;
		this.aplicacao = aplicacao;
	}

	private final Aplicacao aplicacao;

	private final AjusteRepository ajustes;

	private final ProdutoService produtos;

	LocalDate dataAtual;

	public Page<Ajuste> lista(Pageable pageable, AjusteFilter filter) {
		if(filter.getCodigo() != null) {
			return ajustes.lista(filter.getCodigo(), pageable);
		}
		
		return ajustes.lista(pageable);
	}

	public Optional<Ajuste> busca(Long codigo) {
		return ajustes.findById(codigo);
	}

	public Long novo() {
		dataAtual = LocalDate.now();

		Ajuste ajuste = new Ajuste(AjusteStatus.APROCESSAR, aplicacao.getUsuarioAtual(), Date.valueOf(dataAtual));
		return ajustes.save(ajuste).getCodigo();
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public String processar(Long codajuste, String obs) throws AlreadyProcessedException, UnableToChangeStockException, UnableToSaveException {
		dataAtual = LocalDate.now();
		Optional<Ajuste> ajuste = ajustes.findById(codajuste);

		if (!ajuste.isPresent()) {
			throw new AjusteNotFoundException("Ajuste não encontrado");
		}

		final Optional<AjusteStatus> ajusteStatus = ajuste.map(Ajuste::getStatus);
		if (ajusteStatus.isPresent() && ajusteStatus.get().equals(AjusteStatus.PROCESSADO)) {
			throw new AlreadyProcessedException("Ajuste já processado");
		}

		for (int i = 0; i < ajuste.get().getProdutos().size(); i++) {
			Long codprod = ajuste.get().getProdutos().get(i).getProduto().getCodigo();
			int qtdAlteracao = ajuste.get().getProdutos().get(i).getQtd_alteracao();

			EntradaSaida tipo = qtdAlteracao > 0 ? EntradaSaida.ENTRADA : EntradaSaida.SAIDA;
			String origemOperacao = "Referente ao ajuste de estoque " + codajuste;

			try {
				produtos.ajusteEstoque(codprod, qtdAlteracao, tipo, origemOperacao, Date.valueOf(dataAtual));
			} catch (Exception e) {
				Logger.getGlobal().log(Level.SEVERE, e.getMessage());
				throw new UnableToChangeStockException("Erro ao tentar processar o ajuste, chame o suporte");
			}
		}

		ajuste.get().setStatus(AjusteStatus.PROCESSADO);
		ajuste.get().setObservacao(obs);
		ajuste.get().setData_processamento(Date.valueOf(dataAtual));
		try {
			ajustes.save(ajuste.get());
		} catch (Exception e) {
			throw new UnableToSaveException("Erro ao tentar processar o ajuste, chame o suporte");
		}

		return "Ajuste realizado com sucesso";
	}

	public void remover(Ajuste ajuste) throws AlreadyProcessedException, UnableToDeleteException {
		if (ajuste.getStatus().equals(AjusteStatus.PROCESSADO)) {
			throw new AlreadyProcessedException("O ajuste já esta processado");
		}

		try {
			ajustes.deleteById(ajuste.getCodigo());
		} catch (Exception e) {
			throw new UnableToDeleteException("Erro ao tentar cancelar o ajuste");
		}
	}

}
