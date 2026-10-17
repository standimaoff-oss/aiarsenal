"""Read fresh process output using the byte offset returned by stat().st_size."""
def tail_from(path, offset):
    with path.open('rb') as stream:
        stream.seek(offset)
        return stream.read().decode('utf-8', errors='replace')
