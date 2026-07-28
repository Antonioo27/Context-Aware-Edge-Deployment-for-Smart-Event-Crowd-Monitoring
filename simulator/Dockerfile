# Immagine base - Debian minimale con python già pronto
FROM python:3.12-slim

# Variabili d'ambiente che rendono il container pulito e adatto ai log di k8s.
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# Cartella di lavoro dentro il container: tutto il resto e' relativo a qui.
WORKDIR /app


# Copio prima solo le dipendenze e le installo, finche requirements non cambia, Docker riusa questo layer
# anche quando il codice cambia
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Solo ora copio il codice applicativo: cambia spesso, sta in un layer a parte.
COPY src/ ./src/

# Creo un utente non-root e gli dò la proprietà della cartella, in modo tale da poter scrivere il csv di griund truth
RUN useradd --create-home --uid 1000 appuser \
    && chown -R appuser:appuser /app
USER appuser

# Porta del control server
EXPOSE 8081


# Comando di avvio in forma "exec"
CMD ["python", "-m", "simulator.main"]