from fastapi import FastAPI

app = FastAPI(
    title="SwiftDrop AI Service",
    description="AI-powered parcel delivery assistant",
    version="0.1.0",
)


@app.get("/health", tags=["Health"])
def health_check():
    return {"status": "UP"}
