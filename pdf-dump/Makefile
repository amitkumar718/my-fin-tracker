APP_SRC   := ../app/src/main/java
PDF_PKG   := com/ldsa/myfintracker/pdf
EXTRACTOR := $(APP_SRC)/$(PDF_PKG)/PdfTextExtractor.java
DECRYPTOR := $(APP_SRC)/$(PDF_PKG)/PdfDecryptor.java
OUT       := out

# Java 8 to match the Android app's compile target.
JAVAC_FLAGS := --release 8 -Xlint:-options

.PHONY: all build run clean help

all: build

build: | $(OUT)
	javac $(JAVAC_FLAGS) -d $(OUT) -sourcepath $(APP_SRC) \
	      Main.java $(EXTRACTOR) $(DECRYPTOR)

$(OUT):
	mkdir -p $(OUT)

# Usage: make run PDF=/path/to.pdf [PASSWORD=xxx] [FLAGS="--dump --no-mask"]
run: build
	@if [ -z "$(PDF)" ]; then \
	    echo "Usage: make run PDF=/path/to.pdf [PASSWORD=xxx] [FLAGS=--no-mask]"; \
	    exit 2; \
	fi
	@java -cp $(OUT) Main "$(PDF)" \
	    $(if $(PASSWORD),--password "$(PASSWORD)") \
	    $(FLAGS)

clean:
	rm -rf $(OUT)

help:
	@echo "Targets:"
	@echo "  build     compile Main + extractor sources into $(OUT)/"
	@echo "  run       build then run; needs PDF=<path>, optional PASSWORD=<pw>, FLAGS=..."
	@echo "  clean     remove $(OUT)/"
	@echo
	@echo "Examples:"
	@echo "  make build"
	@echo "  make run PDF=/tmp/stmt.pdf"
	@echo "  make run PDF=/tmp/stmt.pdf PASSWORD=secret123"
	@echo "  make run PDF=/tmp/stmt.pdf FLAGS=--no-mask"
	@echo "  make run PDF=/tmp/stmt.pdf FLAGS=--dump    # raw TextChunk positions"
