# Use an official Python runtime as a parent image
FROM python:3.11-slim

# Set the working directory in the container
WORKDIR /app

# Copy the requirements file into the container
COPY DEPLOYMENT.md ./
# Extract requirements from DEPLOYMENT.md or just list them here for clarity
RUN pip install --no-cache-dir fastapi uvicorn sqlalchemy psycopg2-binary alembic pydantic python-dotenv

# Copy the rest of the application code
COPY . .

# Expose the port the app runs on
EXPOSE 8000

# Command to run the application
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
