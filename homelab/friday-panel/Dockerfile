# Imagen base fijada por digest (Dependabot propone las subidas).
FROM python:3.12-slim-bookworm@sha256:6dd8b6dab6b3adc7812bab69eca5dbdc1a5cc690ad28306d234ffb4ef8ac9753
RUN useradd --system --create-home --uid 10001 app
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .
COPY static ./static
ENV PANEL_PORT=8770
EXPOSE 8770
USER app
CMD ["python", "app.py"]
